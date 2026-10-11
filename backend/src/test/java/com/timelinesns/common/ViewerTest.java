package com.timelinesns.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * アクセストークンから操作しているユーザーの ID を取り出す（Viewer）。
 */
class ViewerTest {

    private static Jwt jwt(String subject) {
        return Jwt.withTokenValue("token").header("alg", "HS256").subject(subject).build();
    }

    @Test
    void readsUserIdFromSubject() {
        UUID id = UUID.randomUUID();

        assertThat(Viewer.id(jwt(id.toString()))).isEqualTo(id);
        assertThat(Viewer.idOrNull(jwt(id.toString()))).isEqualTo(id);
    }

    @Test
    void guestIsNull() {
        assertThat(Viewer.idOrNull(null)).isNull();
    }

    @Test
    void loginRequiredWithoutTokenIsUnauthorized() {
        assertThatThrownBy(() -> Viewer.id(null)).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(e.getCode()).isEqualTo("UNAUTHORIZED");
        });
    }

    @Test
    void tokenWithoutSubjectIsTreatedAsGuest() {
        Jwt noSubject = Jwt.withTokenValue("token").header("alg", "HS256").claim("handle", "taro").build();

        assertThat(Viewer.idOrNull(noSubject)).isNull();
        assertThatThrownBy(() -> Viewer.id(noSubject)).isInstanceOf(ApiException.class);
    }
}
