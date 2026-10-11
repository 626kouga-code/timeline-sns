package com.timelinesns.post;

import static com.timelinesns.support.TestImages.animatedGif;
import static com.timelinesns.support.TestImages.image;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.timelinesns.image.ImageProcessor;
import com.timelinesns.storage.ObjectStorage;
import com.timelinesns.storage.StorageCleanup;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/**
 * 投稿の画像の作り直し・保存・削除の登録（PostImageStore）。画像処理は本物を使い、保存先と DB はモックにする。
 */
class PostImageStoreTest {

    private static final UUID POST_ID = UUID.randomUUID();

    private ObjectStorage storage;
    private StorageCleanup cleanup;
    private NamedParameterJdbcTemplate jdbc;
    private PostImageStore store;

    @BeforeEach
    void setUp() {
        storage = mock(ObjectStorage.class);
        cleanup = mock(StorageCleanup.class);
        jdbc = mock(NamedParameterJdbcTemplate.class);
        store = new PostImageStore(new ImageProcessor(), storage, cleanup, jdbc);
    }

    private static MultipartFile file(byte[] content) {
        return new MockMultipartFile("images", "x", "application/octet-stream", content);
    }

    @Test
    void prepareMakesDisplayAndThumbnailSizes() {
        List<PostImageStore.Prepared> prepared = store.prepare(List.of(file(image("png", 4000, 3000))));

        assertThat(prepared).hasSize(1);
        assertThat(prepared.getFirst().display().width()).isEqualTo(PostImageStore.DISPLAY_SIZE);
        assertThat(prepared.getFirst().thumbnail().width()).isEqualTo(PostImageStore.THUMBNAIL_SIZE);
        assertThat(prepared.getFirst().thumbnail().height()).isEqualTo(480);
    }

    @Test
    void prepareKeepsGifAsIsForDisplayOnly() {
        byte[] gif = animatedGif(2);

        PostImageStore.Prepared prepared = store.prepare(List.of(file(gif))).getFirst();

        assertThat(prepared.display().content()).isEqualTo(gif);
        assertThat(prepared.display().contentType()).isEqualTo("image/gif");
        assertThat(prepared.thumbnail().contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void savePutsFilesAndRecordsRowsInGivenOrder() {
        List<PostImageStore.Prepared> prepared = store.prepare(List.of(
                file(image("png", 300, 200)), file(image("png", 100, 400, true))));

        store.save(POST_ID, prepared);

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(storage, times(4)).put(keys.capture(), any(), anyString());
        assertThat(keys.getAllValues()).allMatch(key -> key.startsWith("posts/" + POST_ID + "/"));
        assertThat(keys.getAllValues().get(0)).endsWith(".jpg");
        assertThat(keys.getAllValues().get(1)).endsWith("-thumb.jpg");
        // 透過のある画像は PNG のまま
        assertThat(keys.getAllValues().get(2)).endsWith(".png");

        ArgumentCaptor<SqlParameterSource> rows = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc, times(2)).update(ArgumentMatchers.contains("INSERT INTO post_images"), rows.capture());
        Map<String, Object> first = ((MapSqlParameterSource) rows.getAllValues().get(0)).getValues();
        Map<String, Object> second = ((MapSqlParameterSource) rows.getAllValues().get(1)).getValues();
        assertThat(first).containsEntry("sortOrder", 0).containsEntry("width", 300).containsEntry("height", 200)
                .containsEntry("originalKey", keys.getAllValues().get(0))
                .containsEntry("thumbnailKey", keys.getAllValues().get(1));
        assertThat(second).containsEntry("sortOrder", 1).containsEntry("width", 100).containsEntry("height", 400);

        // 取り消されたら、置いたファイルを全部消す
        verify(cleanup).deleteOnRollback(keys.getAllValues());
    }

    @Test
    void saveRegistersAlreadyStoredFilesEvenWhenFailingHalfway() {
        List<PostImageStore.Prepared> prepared = store.prepare(List.of(file(image("png", 10, 10))));
        doThrow(new IllegalStateException("S3 が落ちた")).when(storage)
                .put(ArgumentMatchers.endsWith("-thumb.jpg"), any(), anyString());

        assertThatThrownBy(() -> store.save(POST_ID, prepared)).isInstanceOf(IllegalStateException.class);

        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(storage, times(2)).put(stored.capture(), any(), anyString());
        verify(cleanup).deleteOnRollback(List.of(stored.getAllValues().getFirst()));
    }

    @Test
    void deleteAfterCommitRegistersAllKeysOfPost() {
        when(jdbc.query(anyString(), any(SqlParameterSource.class), ArgumentMatchers.<RowMapper<List<String>>>any()))
                .thenReturn(List.of(List.of("a.jpg", "a-thumb.jpg"), List.of("b.gif", "b-thumb.jpg")));

        store.deleteAfterCommit(POST_ID);

        verify(cleanup).deleteAfterCommit(eq(List.of("a.jpg", "a-thumb.jpg", "b.gif", "b-thumb.jpg")));
    }
}
