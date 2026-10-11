package com.timelinesns.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.timelinesns.user.Role;
import com.timelinesns.user.User;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * アクセストークン（JWT）の発行（AccessTokenService）。本物の署名で発行し、トークンを解析して中身を確かめる。
 */
class AccessTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");
    private static final String SECRET = "test-secret-for-access-token-0123456789";

    private AccessTokenService service;
    private User user;

    @BeforeEach
    void setUp() {
        SecretKeySpec key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        AuthProperties properties = new AuthProperties(SECRET, Duration.ofMinutes(15), Duration.ofDays(14), true);
        service = new AccessTokenService(NimbusJwtEncoder.withSecretKey(key).build(), properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
        user = new User("taro@example.com", "hash", "taro", "太郎", NOW);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
    }

    @Test
    void issuesSignedTokenWithClaims() throws Exception {
        SignedJWT jwt = SignedJWT.parse(service.issue(user));

        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.HS256);
        assertThat(jwt.verify(new MACVerifier(SECRET.getBytes(StandardCharsets.UTF_8)))).isTrue();
        JWTClaimsSet claims = jwt.getJWTClaimsSet();
        assertThat(claims.getIssuer()).isEqualTo(AccessTokenService.ISSUER);
        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(claims.getIssueTime()).isEqualTo(Date.from(NOW));
        assertThat(claims.getExpirationTime()).isEqualTo(Date.from(NOW.plus(Duration.ofMinutes(15))));
        assertThat(claims.getStringClaim(AccessTokenService.ROLE_CLAIM)).isEqualTo("USER");
        assertThat(claims.getStringClaim("handle")).isEqualTo("taro");
    }

    @Test
    void includesAdminRole() throws Exception {
        user.setRole(Role.ADMIN);

        JWTClaimsSet claims = SignedJWT.parse(service.issue(user)).getJWTClaimsSet();

        assertThat(claims.getStringClaim(AccessTokenService.ROLE_CLAIM)).isEqualTo("ADMIN");
    }

    @Test
    void tokenSignedWithOtherKeyDoesNotVerify() throws Exception {
        SignedJWT jwt = SignedJWT.parse(service.issue(user));

        assertThat(jwt.verify(new MACVerifier("another-secret-another-secret-0123456789"))).isFalse();
    }

    @Test
    void expiresInMatchesTtl() {
        assertThat(service.expiresInSeconds()).isEqualTo(900);
    }
}
