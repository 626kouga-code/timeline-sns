package com.timelinesns.auth;

import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * リフレッシュトークンの Cookie。JavaScript から読めないよう HttpOnly にし、
 * リフレッシュ・ログアウトの API（/api/auth）にだけ送られるよう Path を絞る。
 */
@Component
public class RefreshTokenCookies {

    public static final String NAME = "refresh_token";
    private static final String PATH = "/api/auth";

    private final AuthProperties properties;

    public RefreshTokenCookies(AuthProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie create(String token) {
        return build(token, properties.refreshTokenTtl());
    }

    public ResponseCookie clear() {
        return build("", Duration.ZERO);
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAge)
                .build();
    }
}
