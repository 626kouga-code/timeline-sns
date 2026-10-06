package com.timelinesns.storage;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/**
 * 画像などのファイルの保存先（S3）。キーはバケット内のパス（例: {@code avatars/<ユーザーID>/<UUID>.jpg}）。
 */
@Component
public class ObjectStorage {

    private static final Logger LOG = LoggerFactory.getLogger(ObjectStorage.class);

    private final S3Client s3;
    private final String bucket;

    public ObjectStorage(S3Client s3, StorageProperties properties) {
        this.s3 = s3;
        this.bucket = properties.bucket();
    }

    public void put(String key, byte[] content, String contentType) {
        s3.putObject(b -> b.bucket(bucket).key(key).contentType(contentType), RequestBody.fromBytes(content));
    }

    /**
     * ファイルを取得する。存在しなければ空。
     */
    public Optional<StoredObject> get(String key) {
        try {
            ResponseBytes<GetObjectResponse> object = s3.getObjectAsBytes(b -> b.bucket(bucket).key(key));
            return Optional.of(new StoredObject(object.asByteArray(), object.response().contentType()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    /**
     * ファイルを削除する。不要になったファイルの後片付けに使うので、失敗しても例外にせずログだけ残す
     * （残ったファイルは表示されないだけで、利用者には影響しない）。
     */
    public void deleteQuietly(String key) {
        try {
            s3.deleteObject(b -> b.bucket(bucket).key(key));
        } catch (SdkException e) {
            LOG.warn("ファイルを削除できませんでした: {}", key, e);
        }
    }

    /**
     * 取得したファイル。
     */
    public record StoredObject(byte[] content, String contentType) {

        public StoredObject {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }
}
