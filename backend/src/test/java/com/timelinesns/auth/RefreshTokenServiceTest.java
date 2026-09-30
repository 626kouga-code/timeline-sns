package com.timelinesns.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.timelinesns.common.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();

    private RefreshTokenRepository repository;
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        repository = mock(RefreshTokenRepository.class);
        AuthProperties properties =
                new AuthProperties("x".repeat(32), Duration.ofMinutes(15), Duration.ofDays(14), true);
        service = new RefreshTokenService(repository, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void issueStoresOnlyHashWithFourteenDayExpiry() {
        String raw = service.issue(USER_ID);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getTokenHash()).isEqualTo(RefreshTokenService.hash(raw)).isNotEqualTo(raw);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(14)));
    }

    @Test
    void issuesDifferentTokensEachTime() {
        assertThat(service.issue(USER_ID)).isNotEqualTo(service.issue(USER_ID));
    }

    @Test
    void hashIsSha256Hex() {
        assertThat(RefreshTokenService.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void rotateRevokesCurrentAndIssuesNew() {
        RefreshToken current = new RefreshToken(USER_ID, RefreshTokenService.hash("old"), NOW.plusSeconds(60), NOW);
        when(repository.findByTokenHash(RefreshTokenService.hash("old"))).thenReturn(Optional.of(current));

        RefreshTokenService.Rotation rotation = service.rotate("old");

        assertThat(current.isRevoked()).isTrue();
        assertThat(rotation.userId()).isEqualTo(USER_ID);
        assertThat(rotation.refreshToken()).isNotEqualTo("old");
    }

    @Test
    void rotateWithRevokedTokenRevokesAllTokensOfUser() {
        RefreshToken revoked = new RefreshToken(USER_ID, RefreshTokenService.hash("old"), NOW.plusSeconds(60), NOW);
        revoked.revoke(NOW);
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> service.rotate("old")).isInstanceOf(ApiException.class);
        verify(repository).revokeAllByUserId(USER_ID, NOW);
        verify(repository, never()).save(any());
    }

    @Test
    void rotateRejectsExpiredToken() {
        RefreshToken expired = new RefreshToken(USER_ID, RefreshTokenService.hash("old"), NOW, NOW.minusSeconds(60));
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.rotate("old")).isInstanceOf(ApiException.class);
        verify(repository, never()).save(any());
    }
}
