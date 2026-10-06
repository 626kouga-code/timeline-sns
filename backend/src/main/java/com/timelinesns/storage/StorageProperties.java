package com.timelinesns.storage;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 画像の保存先の設定（application.yml の app.storage）。
 *
 * @param bucket        保存先のバケット
 * @param region        AWS のリージョン
 * @param endpoint      S3 互換ストレージの URL（ローカル・テストの S3Mock）。本番の S3 なら未設定
 * @param publicBaseUrl 画像を配信する URL の先頭。既定はバックエンドの {@code /api/media}。
 *                      本番で CloudFront から配信するときは、その URL に変える
 */
@ConfigurationProperties("app.storage")
public record StorageProperties(
        String bucket,
        String region,
        @Nullable String endpoint,
        String publicBaseUrl) {
}
