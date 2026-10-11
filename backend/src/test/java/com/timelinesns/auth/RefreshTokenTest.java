package com.timelinesns.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * リフレッシュトークンの状態（RefreshToken）。
 */
class RefreshTokenTest {

    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

    private final RefreshToken token = new RefreshToken(UUID.randomUUID(), "hash", NOW.plusSeconds(60), NOW);

    @Test
    void expiresAtExpiryTime() {
        assertThat(token.isExpired(NOW.plusSeconds(59))).isFalse();
        assertThat(token.isExpired(NOW.plusSeconds(60))).isTrue();
    }

    @Test
    void revokeKeepsFirstTime() {
        assertThat(token.isRevoked()).isFalse();

        token.revoke(NOW);
        token.revoke(NOW.plusSeconds(10));

        assertThat(token.isRevoked()).isTrue();
        assertThat(token.getRevokedAt()).isEqualTo(NOW);
    }
}
