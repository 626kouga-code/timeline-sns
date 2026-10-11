package com.timelinesns.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.timelinesns.support.DataAccessTest;
import com.timelinesns.support.Fixtures;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * リフレッシュトークンの保存・検索・一括無効化（JPA と refresh_tokens テーブル）。
 */
@DataAccessTest
class RefreshTokenRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-01T09:00:00Z");
    private static final Instant EXPIRES = NOW.plus(Duration.ofDays(14));

    @Autowired
    private RefreshTokenRepository tokens;

    @Autowired
    private TestEntityManager em;

    @Autowired
    private JdbcTemplate jdbc;

    private Fixtures fixtures;
    private UUID taro;

    @BeforeEach
    void setUp() {
        fixtures = new Fixtures(jdbc);
        taro = fixtures.user("taro");
    }

    @Test
    void savesAndFindsByHash() {
        tokens.saveAndFlush(new RefreshToken(taro, "a".repeat(64), EXPIRES, NOW));
        em.clear();

        RefreshToken found = tokens.findByTokenHash("a".repeat(64)).orElseThrow();

        assertThat(found.getUserId()).isEqualTo(taro);
        assertThat(found.getExpiresAt()).isEqualTo(EXPIRES);
        assertThat(found.isRevoked()).isFalse();
        assertThat(found.isExpired(NOW)).isFalse();
        assertThat(found.isExpired(EXPIRES)).isTrue();
        assertThat(tokens.findByTokenHash("b".repeat(64))).isEmpty();
    }

    @Test
    void persistsRevocation() {
        RefreshToken token = tokens.saveAndFlush(new RefreshToken(taro, "a".repeat(64), EXPIRES, NOW));
        token.revoke(NOW.plusSeconds(60));
        tokens.flush();
        em.clear();

        assertThat(tokens.findByTokenHash("a".repeat(64)).orElseThrow().getRevokedAt())
                .isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void revokeAllRevokesOnlyActiveTokensOfThatUser() {
        UUID hanako = fixtures.user("hanako");
        tokens.save(new RefreshToken(taro, "a".repeat(64), EXPIRES, NOW));
        tokens.save(new RefreshToken(taro, "b".repeat(64), EXPIRES, NOW));
        RefreshToken alreadyRevoked = tokens.save(new RefreshToken(taro, "c".repeat(64), EXPIRES, NOW));
        alreadyRevoked.revoke(NOW.minusSeconds(60));
        tokens.save(new RefreshToken(hanako, "d".repeat(64), EXPIRES, NOW));

        int revoked = tokens.revokeAllByUserId(taro, NOW);

        assertThat(revoked).isEqualTo(2);
        assertThat(tokens.findByTokenHash("a".repeat(64)).orElseThrow().getRevokedAt()).isEqualTo(NOW);
        assertThat(tokens.findByTokenHash("b".repeat(64)).orElseThrow().getRevokedAt()).isEqualTo(NOW);
        // すでに無効なものは、無効にした日時を上書きしない
        assertThat(tokens.findByTokenHash("c".repeat(64)).orElseThrow().getRevokedAt())
                .isEqualTo(NOW.minusSeconds(60));
        // 他のユーザーのトークンには触らない
        assertThat(tokens.findByTokenHash("d".repeat(64)).orElseThrow().isRevoked()).isFalse();
    }

    @Test
    void deletesTokensWithUser() {
        tokens.saveAndFlush(new RefreshToken(taro, "a".repeat(64), EXPIRES, NOW));

        jdbc.update("DELETE FROM users WHERE id = ?", taro);
        em.clear();

        assertThat(tokens.findByTokenHash("a".repeat(64))).isEmpty();
    }

    @Test
    void rejectsDuplicateHash() {
        tokens.saveAndFlush(new RefreshToken(taro, "a".repeat(64), EXPIRES, NOW));

        assertThatThrownBy(() -> tokens.saveAndFlush(new RefreshToken(taro, "a".repeat(64), EXPIRES, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
