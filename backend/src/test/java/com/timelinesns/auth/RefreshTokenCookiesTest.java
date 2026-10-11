package com.timelinesns.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

/**
 * リフレッシュトークンの Cookie の属性（RefreshTokenCookies）。
 */
class RefreshTokenCookiesTest {

    private static RefreshTokenCookies cookies(boolean secure) {
        return new RefreshTokenCookies(
                new AuthProperties("x".repeat(32), Duration.ofMinutes(15), Duration.ofDays(14), secure));
    }

    @Test
    void createSetsSafeAttributes() {
        ResponseCookie cookie = cookies(true).create("token");

        assertThat(cookie.getName()).isEqualTo(RefreshTokenCookies.NAME);
        assertThat(cookie.getValue()).isEqualTo("token");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        // リフレッシュ・ログアウトの API にだけ送る
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(14));
    }

    @Test
    void clearExpiresCookieImmediately() {
        ResponseCookie cookie = cookies(true).clear();

        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
    }

    @Test
    void secureFollowsSetting() {
        assertThat(cookies(false).create("token").isSecure()).isFalse();
    }
}
