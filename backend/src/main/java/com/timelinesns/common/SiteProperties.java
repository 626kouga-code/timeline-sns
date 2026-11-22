package com.timelinesns.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * サイトの設定（application.yml の app.site）。
 *
 * @param publicUrl 利用者がブラウザで開くサイトの URL（末尾の / なし）。OGP の URL・画像は SNS のクローラーが外から読むので、
 *                  この URL を先頭に付けた絶対 URL にする。ローカルは Vite の {@code http://localhost:5173}、
 *                  本番は CloudFront の URL（作り直すたびに変わるので、Terraform が環境変数で渡す）
 */
@ConfigurationProperties("app.site")
public record SiteProperties(String publicUrl) {

    public SiteProperties {
        publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    }

    /**
     * 先頭が / の URL（{@code /media/...} など）をサイトの絶対 URL にする。すでに絶対 URL ならそのまま返す。
     */
    public String absolute(String url) {
        return url.startsWith("http://") || url.startsWith("https://") ? url : publicUrl + url;
    }
}
