package com.timelinesns.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.timelinesns.support.DataAccessTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * ユーザーの保存・読み出し（JPA と users テーブル）。保存したあと永続化コンテキストを空にして、DB から読み直して確かめる。
 */
@DataAccessTest
class UserRepositoryTest {

    // DB の timestamptz はマイクロ秒までなので、比べる値もそろえておく
    private static final Instant CREATED_AT = Instant.parse("2026-10-01T09:00:00.123456Z");

    @Autowired
    private UserRepository users;

    @Autowired
    private TestEntityManager em;

    @Test
    void savesAndReadsBackAllColumns() {
        User saved = users.saveAndFlush(new User("taro@example.com", "hash", "taro", "太郎", CREATED_AT));
        em.clear();

        User found = users.findById(saved.getId()).orElseThrow();

        assertThat(found.getEmail()).isEqualTo("taro@example.com");
        assertThat(found.getPasswordHash()).isEqualTo("hash");
        assertThat(found.getHandle()).isEqualTo("taro");
        assertThat(found.getDisplayName()).isEqualTo("太郎");
        assertThat(found.getCreatedAt()).isEqualTo(CREATED_AT);
        // 既定値
        assertThat(found.getBio()).isEmpty();
        assertThat(found.getAvatarKey()).isNull();
        assertThat(found.getEmailVerifiedAt()).isNull();
        assertThat(found.getRole()).isEqualTo(Role.USER);
        assertThat(found.getStatus()).isEqualTo(UserStatus.ACTIVE);
        // ID はアプリ側で UUIDv7 を採番する
        assertThat(found.getId().version()).isEqualTo(7);
    }

    @Test
    void persistsProfileAvatarRoleAndStatusChanges() {
        User user = users.saveAndFlush(new User("taro@example.com", "hash", "taro", "太郎", CREATED_AT));
        user.updateProfile("  新しい名前  ", "  よろしく  ");
        user.changeAvatar("avatars/a.jpg");
        user.markEmailVerified(CREATED_AT.plus(1, ChronoUnit.HOURS));
        user.setRole(Role.ADMIN);
        user.setStatus(UserStatus.SUSPENDED);
        users.flush();
        em.clear();

        User found = users.findById(user.getId()).orElseThrow();

        assertThat(found.getDisplayName()).isEqualTo("新しい名前");
        assertThat(found.getBio()).isEqualTo("よろしく");
        assertThat(found.getAvatarKey()).isEqualTo("avatars/a.jpg");
        assertThat(found.getEmailVerifiedAt()).isEqualTo(CREATED_AT.plus(1, ChronoUnit.HOURS));
        assertThat(found.getRole()).isEqualTo(Role.ADMIN);
        assertThat(found.isSuspended()).isTrue();
    }

    @Test
    void findsAndChecksEmailIgnoringCase() {
        users.saveAndFlush(new User("Taro@Example.com", "hash", "taro", "太郎", CREATED_AT));
        em.clear();

        assertThat(users.findByEmailIgnoreCase("taro@EXAMPLE.COM")).map(User::getHandle).contains("taro");
        assertThat(users.findByEmailIgnoreCase("hanako@example.com")).isEmpty();
        assertThat(users.existsByEmailIgnoreCase("TARO@example.com")).isTrue();
        assertThat(users.existsByEmailIgnoreCase("hanako@example.com")).isFalse();
    }

    @Test
    void checksHandleIgnoringCase() {
        users.saveAndFlush(new User("taro@example.com", "hash", "Taro_1", "太郎", CREATED_AT));

        assertThat(users.existsByHandleIgnoreCase("taro_1")).isTrue();
        assertThat(users.existsByHandleIgnoreCase("taro")).isFalse();
    }

    // PostgreSQL では制約違反のあとトランザクションが使えなくなるので、違反の確認は 1 テストに 1 つ
    @Test
    void rejectsSameHandleWithDifferentCase() {
        users.saveAndFlush(new User("taro@example.com", "hash", "taro", "太郎", CREATED_AT));

        assertThatThrownBy(() -> users.saveAndFlush(new User("other@example.com", "hash", "TARO", "別人", CREATED_AT)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsSameEmailWithDifferentCase() {
        users.saveAndFlush(new User("taro@example.com", "hash", "taro", "太郎", CREATED_AT));

        assertThatThrownBy(() -> users.saveAndFlush(new User("TARO@example.com", "hash", "other", "別人", CREATED_AT)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsHandleOutsideAllowedFormat() {
        assertThatThrownBy(() -> users.saveAndFlush(new User("taro@example.com", "hash", "ta-ro", "太郎", CREATED_AT)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
