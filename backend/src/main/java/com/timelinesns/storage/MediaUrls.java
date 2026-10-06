package com.timelinesns.storage;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 保存したファイルのキーから、画面で表示する URL を作る。
 */
@Component
public class MediaUrls {

    private final String baseUrl;

    public MediaUrls(StorageProperties properties) {
        String base = properties.publicBaseUrl();
        this.baseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /**
     * キーが null（画像なし）なら null を返す。
     */
    public @Nullable String url(@Nullable String key) {
        return key == null ? null : baseUrl + "/" + key;
    }
}
