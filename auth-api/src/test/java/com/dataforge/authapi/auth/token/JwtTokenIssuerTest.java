package com.dataforge.authapi.auth.token;

import com.dataforge.authapi.auth.model.AccessToken;
import com.dataforge.authapi.auth.model.UserRole;
import com.dataforge.authapi.config.JwtConfig;
import com.dataforge.authapi.config.JwtTestConfiguration;
import com.dataforge.authapi.security.JwtTokenIssuer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@SpringJUnitConfig({
        JwtConfig.class,
        JwtTestConfiguration.class,
        JwtTokenIssuer.class,
        JwtTokenIssuerTest.FixedClockConfiguration.class
})
class JwtTokenIssuerTest {

    private static final UUID USER_ID = UUID.fromString("4eeaa832-8a05-4c00-b45c-5a1c18746a41");
    private static final String ISSUER = "reserveflow-test-auth";
    private static final String AUDIENCE = "reserveflow-test-api";
    private static final String TTL = "PT5M";
    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00.987654321Z");
    private static final Instant ISSUED_AT = Instant.parse("2026-11-13T10:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-11-13T10:05:00Z");

    @Autowired
    private TokenIssuer tokenIssuer;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Value("${app.jwt.public-key-location}")
    private Resource publicKeyResource;

    private RSAPublicKey publicKey;

    @BeforeEach
    void setUp() throws IOException {
        try (InputStream stream = publicKeyResource.getInputStream()) {
            publicKey = RsaKeyConverters.x509().convert(stream);
        }
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void issuesSignedTokenWithConfiguredClaimsAndExpiration(UserRole role) {
        AccessToken token = tokenIssuer.issue(USER_ID, role);

        Jwt jwt = decoder(ISSUED_AT).decode(token.value());
        assertEquals("RS256", jwt.getHeaders().get("alg"));
        assertEquals("JWT", jwt.getHeaders().get("typ"));
        assertEquals(ISSUER, jwt.getClaimAsString("iss"));
        assertEquals(List.of(AUDIENCE), jwt.getAudience());
        assertEquals(USER_ID.toString(), jwt.getSubject());
        assertEquals(role.name(), jwt.getClaimAsString("role"));
        assertEquals(ISSUED_AT, jwt.getIssuedAt());
        assertEquals(EXPIRES_AT, jwt.getExpiresAt());
        assertEquals(jwt.getExpiresAt(), token.expiresAt());
        assertNotNull(jwt.getId());
        UUID.fromString(jwt.getId());
    }

    @Test
    void issuesDistinctTokensForSameUserAtSameInstant() {
        AccessToken first = tokenIssuer.issue(USER_ID, UserRole.USER);
        AccessToken second = tokenIssuer.issue(USER_ID, UserRole.USER);

        JwtDecoder decoder = decoder(ISSUED_AT);
        Jwt firstJwt = decoder.decode(first.value());
        Jwt secondJwt = decoder.decode(second.value());

        assertNotEquals(first.value(), second.value());
        assertNotEquals(firstJwt.getId(), secondJwt.getId());
        assertEquals(first.expiresAt(), second.expiresAt());
    }

    @Test
    void readsCurrentTimeForEachIssuedToken() {
        Clock clock = mock(Clock.class);
        given(clock.instant()).willReturn(NOW, NOW.plusSeconds(60));
        JwtTokenIssuer issuer = new JwtTokenIssuer(jwtEncoder, clock, ISSUER, AUDIENCE, TTL);

        AccessToken first = issuer.issue(USER_ID, UserRole.USER);
        AccessToken second = issuer.issue(USER_ID, UserRole.USER);

        assertEquals(EXPIRES_AT, first.expiresAt());
        assertEquals(EXPIRES_AT.plusSeconds(60), second.expiresAt());
        assertEquals(ISSUED_AT, decoder(ISSUED_AT).decode(first.value()).getIssuedAt());
        assertEquals(
                ISSUED_AT.plusSeconds(60),
                decoder(ISSUED_AT.plusSeconds(60)).decode(second.value()).getIssuedAt()
        );
    }

    @Test
    void rejectsTokenAfterExpiration() {
        AccessToken token = tokenIssuer.issue(USER_ID, UserRole.USER);
        JwtDecoder decoder = decoder(EXPIRES_AT.plusSeconds(1));

        assertThrows(JwtValidationException.class, () -> decoder.decode(token.value()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "-PT1S", "PT0.5S", "PT1.5S"})
    void rejectsNonPositiveOrFractionalTtl(String ttl) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new JwtTokenIssuer(jwtEncoder, Clock.fixed(NOW, ZoneOffset.UTC), ISSUER, AUDIENCE, ttl)
        );

        assertEquals(
                "Access token TTL must be a positive whole number of seconds",
                exception.getMessage()
        );
    }

    @Test
    void rejectsMalformedTtl() {
        assertThrows(
                DateTimeParseException.class,
                () -> new JwtTokenIssuer(
                        jwtEncoder,
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        ISSUER,
                        AUDIENCE,
                        "15 minutes"
                )
        );
    }

    private JwtDecoder decoder(Instant currentTime) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ZERO);
        timestampValidator.setClock(Clock.fixed(currentTime, ZoneOffset.UTC));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestampValidator,
                new JwtIssuerValidator(ISSUER),
                new JwtAudienceValidator(AUDIENCE)
        ));
        return decoder;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
