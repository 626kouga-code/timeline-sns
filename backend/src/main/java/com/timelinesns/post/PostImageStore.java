package com.timelinesns.post;

import com.timelinesns.image.ImageProcessor;
import com.timelinesns.image.ImageProcessor.DecodedImage;
import com.timelinesns.image.ImageProcessor.ProcessedImage;
import com.timelinesns.storage.ObjectStorage;
import com.timelinesns.storage.StorageCleanup;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 投稿の画像（F-11）。表示用（長辺 2048px）とサムネイル（長辺 640px）を作って保存先に置き、post_images に記録する。
 * GIF は表示用だけ元のファイルのまま（アニメーションを残す）にし、サムネイルは 1 枚目から作る。
 */
@Component
public class PostImageStore {

    /** 表示用（拡大表示）の長辺 */
    static final int DISPLAY_SIZE = 2048;
    /** サムネイル（一覧）の長辺 */
    static final int THUMBNAIL_SIZE = 640;

    private final ImageProcessor processor;
    private final ObjectStorage storage;
    private final StorageCleanup cleanup;
    private final NamedParameterJdbcTemplate jdbc;

    public PostImageStore(ImageProcessor processor, ObjectStorage storage, StorageCleanup cleanup,
            NamedParameterJdbcTemplate jdbc) {
        this.processor = processor;
        this.storage = storage;
        this.cleanup = cleanup;
        this.jdbc = jdbc;
    }

    /**
     * アップロードされた画像を作り直す。保存先には何も置かないので、形式違反などで失敗しても後片付けは要らない。
     */
    public List<Prepared> prepare(List<MultipartFile> files) {
        List<Prepared> prepared = new ArrayList<>();
        for (MultipartFile file : files) {
            DecodedImage decoded = processor.decode(bytes(file));
            ProcessedImage display = decoded.isGif()
                    ? processor.originalGif(decoded)
                    : processor.fit(decoded, DISPLAY_SIZE);
            prepared.add(new Prepared(display, processor.fit(decoded, THUMBNAIL_SIZE)));
        }
        return prepared;
    }

    /**
     * 作り直した画像を保存先に置き、post_images に記録する（並び順は渡した順）。
     * トランザクションが取り消されたら、置いた画像を消す。
     */
    public void save(UUID postId, List<Prepared> images) {
        List<String> stored = new ArrayList<>();
        try {
            saveAll(postId, images, stored);
        } finally {
            // 途中で失敗した場合も、それまでに置いたファイルを取り消しのときに消す
            cleanup.deleteOnRollback(stored);
        }
    }

    private void saveAll(UUID postId, List<Prepared> images, List<String> stored) {
        for (int i = 0; i < images.size(); i++) {
            Prepared image = images.get(i);
            String base = "posts/" + postId + "/" + UUID.randomUUID();
            String originalKey = base + "." + image.display().extension();
            String thumbnailKey = base + "-thumb." + image.thumbnail().extension();
            storage.put(originalKey, image.display().content(), image.display().contentType());
            stored.add(originalKey);
            storage.put(thumbnailKey, image.thumbnail().content(), image.thumbnail().contentType());
            stored.add(thumbnailKey);
            jdbc.update("""
                    INSERT INTO post_images (post_id, original_key, thumbnail_key, width, height, sort_order)
                    VALUES (:postId, :originalKey, :thumbnailKey, :width, :height, :sortOrder)
                    """, new MapSqlParameterSource()
                    .addValue("postId", postId)
                    .addValue("originalKey", originalKey)
                    .addValue("thumbnailKey", thumbnailKey)
                    .addValue("width", image.display().width())
                    .addValue("height", image.display().height())
                    .addValue("sortOrder", i));
        }
    }

    /**
     * 投稿の削除が確定したら、その投稿の画像を保存先から消す（post_images の行は DB の ON DELETE CASCADE で消える）。
     */
    public void deleteAfterCommit(UUID postId) {
        List<String> keys = jdbc.query(
                "SELECT original_key, thumbnail_key FROM post_images WHERE post_id = :postId",
                new MapSqlParameterSource("postId", postId),
                (rs, rowNum) -> List.of(rs.getString("original_key"), rs.getString("thumbnail_key")))
                .stream().flatMap(List::stream).toList();
        cleanup.deleteAfterCommit(keys);
    }

    private static byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 作り直した 1 枚分（表示用とサムネイル）。
     */
    public record Prepared(ProcessedImage display, ProcessedImage thumbnail) {
    }
}
