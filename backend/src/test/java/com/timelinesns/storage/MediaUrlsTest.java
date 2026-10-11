package com.timelinesns.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 保存したファイルのキーから表示用の URL を作る（MediaUrls）。
 */
class MediaUrlsTest {

    private static MediaUrls urls(String base) {
        return new MediaUrls(new StorageProperties("bucket", "ap-northeast-1", null, base));
    }

    @Test
    void joinsBaseUrlAndKey() {
        assertThat(urls("/api/media").url("avatars/a.jpg")).isEqualTo("/api/media/avatars/a.jpg");
    }

    @Test
    void ignoresTrailingSlashOfBaseUrl() {
        assertThat(urls("https://cdn.example.com/").url("posts/p.jpg")).isEqualTo("https://cdn.example.com/posts/p.jpg");
    }

    @Test
    void nullKeyMeansNoImage() {
        assertThat(urls("/api/media").url(null)).isNull();
    }
}
