package com.timelinesns.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 認証まわりの設定（application.yml の app.auth）。
 *
 * @param jwtSecret       アクセストークン（JWT, HS256）の署名鍵。32 バイト以上
 * @param accessTokenTtl  アクセストークンの有効期間（15 分）
 * @param refreshTokenTtl リフレッシュトークンの有効期間（14 日）
 * @param cookieSecure    リフレッシュトークンの Cookie に Secure 属性を付けるか
 */
@ConfigurationProperties("app.auth")
public record AuthProperties(
        String jwtSecret,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        boolean cookieSecure) {
}
