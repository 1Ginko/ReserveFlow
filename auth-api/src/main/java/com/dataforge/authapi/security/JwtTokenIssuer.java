package com.dataforge.authapi.security;

import com.dataforge.authapi.auth.model.AccessToken;
import com.dataforge.authapi.auth.model.UserRole;
import com.dataforge.authapi.auth.token.TokenIssuer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Component
public class JwtTokenIssuer implements TokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final Clock clock;
    private final String issuer;
    private final String audience;
    private final Duration accessTokenTtl;

    public JwtTokenIssuer(
            JwtEncoder jwtEncoder,
            Clock clock,
            @Value("${app.jwt.issuer}") String issuer,
            @Value("${app.jwt.audience}") String audience,
            @Value("${app.jwt.access-token-ttl}") String accessTokenTtl
    ) {
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
        this.issuer = issuer;
        this.audience = audience;
        this.accessTokenTtl = Duration.parse(accessTokenTtl);

        if (!this.accessTokenTtl.isPositive() || this.accessTokenTtl.getNano() != 0) {
            throw new IllegalArgumentException("Access token TTL must be a positive whole number of seconds");
        }
    }

    @Override
    public AccessToken issue(UUID userId, UserRole role) {
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(accessTokenTtl);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .subject(userId.toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("role", role.name())
                .build();

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .type("JWT")
                .build();

        Jwt jwt = jwtEncoder.encode(JwtEncoderParameters.from(header, claims));

        return new AccessToken(jwt.getTokenValue(), expiresAt);
    }
}