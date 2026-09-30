package com.timelinesns.auth;

import com.timelinesns.user.User;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * アクセストークン（JWT）を発行する。フロントはメモリにだけ保持し、API には Authorization: Bearer で送る。
 */
@Service
public class AccessTokenService {

    static final String ISSUER = "timeline-sns";
    static final String ROLE_CLAIM = "role";

    private final JwtEncoder encoder;
    private final AuthProperties properties;
    private final Clock clock;

    public AccessTokenService(JwtEncoder encoder, AuthProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public String issue(User user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .claim(ROLE_CLAIM, user.getRole().name())
                .claim("handle", user.getHandle())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long expiresInSeconds() {
        return properties.accessTokenTtl().toSeconds();
    }
}
