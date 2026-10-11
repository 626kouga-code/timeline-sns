package com.timelinesns.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * ユーザーのドメインの操作（User）。
 */
class UserTest {

    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

    private final User user = new User("taro@example.com", "hash", "taro", "太郎", NOW);

    @Test
    void newUserIsActiveUnverifiedUser() {
        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.isSuspended()).isFalse();
        assertThat(user.getEmailVerifiedAt()).isNull();
        assertThat(user.getBio()).isEmpty();
        assertThat(user.getAvatarKey()).isNull();
    }

    @Test
    void updateProfileStripsSurroundingSpaces() {
        user.updateProfile("　 新しい名前 \n", "  よろしく  ");

        assertThat(user.getDisplayName()).isEqualTo("新しい名前");
        assertThat(user.getBio()).isEqualTo("よろしく");
    }

    @Test
    void changeAvatarReturnsPreviousKey() {
        assertThat(user.changeAvatar("avatars/1.jpg")).isNull();
        assertThat(user.changeAvatar("avatars/2.jpg")).isEqualTo("avatars/1.jpg");
        assertThat(user.changeAvatar(null)).isEqualTo("avatars/2.jpg");
        assertThat(user.getAvatarKey()).isNull();
    }

    @Test
    void markEmailVerifiedKeepsFirstTime() {
        user.markEmailVerified(NOW);
        user.markEmailVerified(NOW.plusSeconds(60));

        assertThat(user.getEmailVerifiedAt()).isEqualTo(NOW);
    }

    @Test
    void suspended() {
        user.setStatus(UserStatus.SUSPENDED);

        assertThat(user.isSuspended()).isTrue();
    }
}
