package com.dataforge.authapi.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringJUnitConfig({JwtConfig.class, JwtTestConfiguration.class})
class JwtConfigTest {

    private static final UUID USER_ID = UUID.fromString("4eeaa832-8a05-4c00-b45c-5a1c18746a41");
    private static final UUID OTHER_USER_ID = UUID.fromString("82fe9011-656b-46ce-83ba-2d211a5b33c2");
    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");

    @Autowired
    private JwtEncoder jwtEncoder;

    @Value("${app.jwt.public-key-location}")
    private Resource publicKeyResource;

    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() throws IOException {
        try (InputStream stream = publicKeyResource.getInputStream()) {
            RSAPublicKey publicKey = RsaKeyConverters.x509().convert(stream);
            jwtDecoder = decoder(publicKey);
        }
    }

    @Test
    void signsJwtWithRs256AndConfiguredKeyPair() {
        Jwt jwt = jwtDecoder.decode(token());

        assertEquals("RS256", jwt.getHeaders().get("alg"));
        assertEquals(USER_ID.toString(), jwt.getSubject());
        assertEquals(NOW, jwt.getIssuedAt());
        assertEquals(NOW.plusSeconds(900), jwt.getExpiresAt());
        assertEquals("USER", jwt.getClaimAsString("role"));
    }

    @Test
    void rejectsTokenWhenPublicKeyDoesNotMatch() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        RSAPublicKey otherPublicKey = (RSAPublicKey) generator.generateKeyPair().getPublic();
        JwtDecoder otherDecoder = decoder(otherPublicKey);
        String token = token();

        assertThrows(BadJwtException.class, () -> otherDecoder.decode(token));
    }

    @Test
    void rejectsTokenWithModifiedPayload() {
        String[] parts = token().split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String modifiedPayload = payload.replace(USER_ID.toString(), OTHER_USER_ID.toString());
        assertNotEquals(payload, modifiedPayload);

        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(modifiedPayload.getBytes(StandardCharsets.UTF_8));
        String modifiedToken = String.join(".", parts[0], encodedPayload, parts[2]);

        assertThrows(BadJwtException.class, () -> jwtDecoder.decode(modifiedToken));
    }

    private String token() {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(USER_ID.toString())
                .issuedAt(NOW)
                .expiresAt(NOW.plusSeconds(900))
                .claim("role", "USER")
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .type("JWT")
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private JwtDecoder decoder(RSAPublicKey publicKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ZERO);
        timestampValidator.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
        decoder.setJwtValidator(timestampValidator);
        return decoder;
    }
}
