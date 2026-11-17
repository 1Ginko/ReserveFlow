package com.dataforge.authapi.auth;

import com.dataforge.authapi.auth.commands.LoginUserCommand;
import com.dataforge.authapi.auth.commands.RegisterUserCommand;
import com.dataforge.authapi.auth.model.AccessToken;
import com.dataforge.authapi.auth.model.UserRole;
import com.dataforge.authapi.config.JwtTestConfiguration;
import com.dataforge.authapi.config.PostgresTestConfiguration;
import com.dataforge.authapi.data.repository.UserRepository;
import com.dataforge.authapi.exceptions.InvalidCredentialsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@Import({PostgresTestConfiguration.class, JwtTestConfiguration.class})
class AuthLoginIntegrationTest {

    private static final String EMAIL = "oleg@example.com";
    private static final String PASSWORD = " my-long-password-123 ";
    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00.123456Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-11-13T10:05:00Z");

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Clock clock;

    @Value("${app.jwt.public-key-location}")
    private Resource publicKeyResource;

    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() throws IOException {
        userRepository.deleteAllInBatch();
        given(clock.instant()).willReturn(NOW);

        try (InputStream stream = publicKeyResource.getInputStream()) {
            RSAPublicKey publicKey = RsaKeyConverters.x509().convert(stream);
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey)
                    .signatureAlgorithm(SignatureAlgorithm.RS256)
                    .build();
            JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ZERO);
            timestampValidator.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    timestampValidator,
                    new JwtIssuerValidator("reserveflow-test-auth"),
                    new JwtAudienceValidator("reserveflow-test-api")
            ));
            jwtDecoder = decoder;
        }
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void logsInRegisteredUserAndSignsTokenWithStoredRole(UserRole role) {
        UUID userId = authService.register(new RegisterUserCommand(EMAIL, PASSWORD));
        jdbcTemplate.update("UPDATE users SET user_role = ? WHERE user_id = ?", role.name(), userId);

        AccessToken token = authService.login(new LoginUserCommand("  OLEG@EXAMPLE.COM  ", PASSWORD));

        Jwt jwt = jwtDecoder.decode(token.value());
        assertEquals(userId.toString(), jwt.getSubject());
        assertEquals(role.name(), jwt.getClaimAsString("role"));
        assertEquals("RS256", jwt.getHeaders().get("alg"));
        assertEquals(EXPIRES_AT, jwt.getExpiresAt());
        assertEquals(jwt.getExpiresAt(), token.expiresAt());
        assertEquals(1L, userRepository.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong-long-password", "short", "my-long-password-123"})
    void rejectsIncorrectPasswordAndKeepsExistingAccount(String password) {
        UUID userId = authService.register(new RegisterUserCommand(EMAIL, PASSWORD));
        String storedHash = userRepository.findById(userId).orElseThrow().getPasswordHash();

        InvalidCredentialsException exception = assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(new LoginUserCommand(EMAIL, password))
        );

        assertEquals("Invalid email or password", exception.getMessage());
        assertEquals(1L, userRepository.count());
        assertEquals(storedHash, userRepository.findById(userId).orElseThrow().getPasswordHash());
    }

    @Test
    void rejectsUnknownEmailWithoutCreatingAccount() {
        InvalidCredentialsException exception = assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(new LoginUserCommand("missing@example.com", PASSWORD))
        );

        assertEquals("Invalid email or password", exception.getMessage());
        assertEquals(0L, userRepository.count());
    }
}
