package com.timelinesns.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.timelinesns.common.ApiException;
import com.timelinesns.image.ImageProcessor;
import com.timelinesns.storage.MediaUrls;
import com.timelinesns.storage.ObjectStorage;
import com.timelinesns.storage.StorageCleanup;
import com.timelinesns.storage.StorageProperties;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * アイコン画像の変更・削除（AvatarService）。画像処理・保存先・後片付けはモックにし、
 * 新しいキーで保存すること、古い画像・新しい画像をどの時点で消すよう登録するかを確かめる。
 */
class AvatarServiceTest {

    private static final UUID ME = UUID.randomUUID();
    private static final byte[] UPLOAD = {1, 2, 3};

    private UserRepository users;
    private ImageProcessor images;
    private ObjectStorage storage;
    private StorageCleanup cleanup;
    private AvatarService service;
    private User user;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        images = mock(ImageProcessor.class);
        storage = mock(ObjectStorage.class);
        cleanup = mock(StorageCleanup.class);
        MediaUrls mediaUrls = new MediaUrls(new StorageProperties("b", "r", null, "/api/media"));
        service = new AvatarService(users, images, storage, cleanup, mediaUrls);
        user = new User("taro@example.com", "hash", "taro", "太郎", Instant.now());
        ReflectionTestUtils.setField(user, "id", ME);
        when(users.findById(ME)).thenReturn(Optional.of(user));
        when(images.squareThumbnail(UPLOAD, AvatarService.SIZE))
                .thenReturn(new ImageProcessor.ProcessedImage(new byte[] {9}, "image/jpeg", "jpg", 400, 400));
    }

    @Test
    void changeStoresSquareImageUnderNewKey() {
        MeResponse me = service.change(ME, UPLOAD);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).put(key.capture(), eq(new byte[] {9}), eq("image/jpeg"));
        assertThat(key.getValue()).matches("avatars/" + ME + "/[0-9a-f-]{36}\\.jpg");
        assertThat(user.getAvatarKey()).isEqualTo(key.getValue());
        assertThat(me.avatarUrl()).isEqualTo("/api/media/" + key.getValue());
        // DB の更新が取り消されたら、新しく置いた画像を消す
        verify(cleanup).deleteOnRollback(List.of(key.getValue()));
        // 差し替える前の画像はないので、消すものはない
        verify(cleanup).deleteAfterCommit(List.of());
    }

    @Test
    void changeDeletesPreviousAvatarAfterCommit() {
        user.changeAvatar("avatars/old.jpg");

        service.change(ME, UPLOAD);

        verify(cleanup).deleteAfterCommit(List.of("avatars/old.jpg"));
    }

    @Test
    void eachChangeUsesDifferentKey() {
        service.change(ME, UPLOAD);
        String first = user.getAvatarKey();
        service.change(ME, UPLOAD);

        assertThat(user.getAvatarKey()).isNotEqualTo(first);
    }

    @Test
    void removeClearsAvatarAndDeletesFileAfterCommit() {
        user.changeAvatar("avatars/old.jpg");

        MeResponse me = service.remove(ME);

        assertThat(user.getAvatarKey()).isNull();
        assertThat(me.avatarUrl()).isNull();
        verify(cleanup).deleteAfterCommit(List.of("avatars/old.jpg"));
        verify(storage, never()).put(anyString(), any(), anyString());
    }

    @Test
    void deletedUserIsUnauthorized() {
        UUID deleted = UUID.randomUUID();

        assertThatThrownBy(() -> service.change(deleted, UPLOAD))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(e.getCode()).isEqualTo("USER_NOT_FOUND");
                });
        verify(storage, never()).put(anyString(), any(), anyString());
    }
}
