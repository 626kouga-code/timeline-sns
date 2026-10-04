package com.timelinesns.common;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * アクセストークン（JWT）の {@code sub} から、操作しているユーザーの ID を取り出す。
 */
public final class Viewer {

    private Viewer() {
    }

    /**
     * ゲストも使える API 用。未ログインなら null。
     */
    public static @Nullable UUID idOrNull(@Nullable Jwt jwt) {
        String subject = jwt == null ? null : jwt.getSubject();
        return subject == null ? null : UUID.fromString(subject);
    }

    /**
     * ログイン必須の API 用。
     */
    public static UUID id(@Nullable Jwt jwt) {
        UUID id = idOrNull(jwt);
        if (id == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "ログインしてください");
        }
        return id;
    }
}
