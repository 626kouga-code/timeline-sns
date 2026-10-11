package com.timelinesns.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.timelinesns.support.DataAccessTest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 画像の保存先（ObjectStorage）。Testcontainers の S3Mock に実際に保存して、取り出して確かめる。
 * S3 はトランザクションで戻らないので、キーはテストごとに新しく作る。
 */
@DataAccessTest
class ObjectStorageTest {

    @Autowired
    private ObjectStorage storage;

    private static String newKey() {
        return "test/" + UUID.randomUUID() + ".png";
    }

    @Test
    void putThenGetReturnsSameContentAndType() {
        String key = newKey();
        byte[] content = {(byte) 0x89, 'P', 'N', 'G', 0, 1, 2, 3};

        storage.put(key, content, "image/png");

        ObjectStorage.StoredObject stored = storage.get(key).orElseThrow();
        assertThat(stored.content()).isEqualTo(content);
        assertThat(stored.contentType()).isEqualTo("image/png");
    }

    @Test
    void putOverwritesExistingKey() {
        String key = newKey();
        storage.put(key, "old".getBytes(StandardCharsets.UTF_8), "text/plain");

        storage.put(key, "new".getBytes(StandardCharsets.UTF_8), "image/jpeg");

        ObjectStorage.StoredObject stored = storage.get(key).orElseThrow();
        assertThat(new String(stored.content(), StandardCharsets.UTF_8)).isEqualTo("new");
        assertThat(stored.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void getReturnsEmptyForMissingKey() {
        assertThat(storage.get(newKey())).isEmpty();
    }

    @Test
    void deleteQuietlyRemovesObject() {
        String key = newKey();
        storage.put(key, new byte[] {1}, "image/png");

        storage.deleteQuietly(key);

        assertThat(storage.get(key)).isEmpty();
    }

    @Test
    void deleteQuietlyIgnoresMissingKey() {
        assertThatCode(() -> storage.deleteQuietly(newKey())).doesNotThrowAnyException();
    }

    @Test
    void storedObjectIsNotChangedThroughReturnedArray() {
        String key = newKey();
        storage.put(key, new byte[] {1, 2, 3}, "image/png");
        ObjectStorage.StoredObject stored = storage.get(key).orElseThrow();

        stored.content()[0] = 9;

        assertThat(stored.content()).containsExactly(1, 2, 3);
    }
}
