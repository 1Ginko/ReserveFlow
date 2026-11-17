package com.dataforge.authapi.auth;

import com.dataforge.authapi.auth.commands.LoginUserCommand;
import com.dataforge.authapi.auth.commands.RegisterUserCommand;
import com.dataforge.authapi.auth.model.AccessToken;
import com.dataforge.authapi.auth.model.User;
import com.dataforge.authapi.auth.model.UserRole;
import com.dataforge.authapi.auth.storage.UserStorage;
import com.dataforge.authapi.auth.token.TokenIssuer;
import com.dataforge.authapi.exceptions.InvalidCredentialsException;
import com.dataforge.authapi.exceptions.UserAlreadyExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final UUID USER_ID = UUID.fromString("4eeaa832-8a05-4c00-b45c-5a1c18746a41");
    private static final String EMAIL = "oleg@example.com";
    private static final String PASSWORD = " my-long-password-123 ";
    private static final String PASSWORD_HASH = "{bcrypt}test-hash";
    private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");

    @Mock
    private UserStorage userStorage;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenIssuer tokenIssuer;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                userStorage,
                passwordEncoder,
                tokenIssuer,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void registersWithNormalizedEmailEncodedPasswordAndCurrentTime() {
        RegisterUserCommand command = new RegisterUserCommand("  OLEG@EXAMPLE.COM  ", PASSWORD);
        given(passwordEncoder.encode(PASSWORD)).willReturn(PASSWORD_HASH);
        given(userStorage.create(any(UUID.class), eq(EMAIL), eq(PASSWORD_HASH), eq(NOW)))
                .willReturn(true);

        UUID userId = authService.register(command);

        ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
        then(userStorage).should().create(idCaptor.capture(), eq(EMAIL), eq(PASSWORD_HASH), eq(NOW));
        then(passwordEncoder).should().encode(PASSWORD);
        assertNotNull(userId);
        assertEquals(userId, idCaptor.getValue());
    }

    @Test
    void rejectsAlreadyRegisteredEmail() {
        RegisterUserCommand command = new RegisterUserCommand("OLEG@EXAMPLE.COM", PASSWORD);
        given(passwordEncoder.encode(PASSWORD)).willReturn(PASSWORD_HASH);
        given(userStorage.create(any(UUID.class), eq(EMAIL), eq(PASSWORD_HASH), eq(NOW)))
                .willReturn(false);

        UserAlreadyExistsException exception = assertThrows(
                UserAlreadyExistsException.class,
                () -> authService.register(command)
        );

        assertEquals("User with email already exists: " + EMAIL, exception.getMessage());
        then(userStorage).should().create(any(UUID.class), eq(EMAIL), eq(PASSWORD_HASH), eq(NOW));
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void logsInWithNormalizedEmailAndRoleFromStoredUser(UserRole role) {
        User user = new User(USER_ID, EMAIL, PASSWORD_HASH, role, NOW, NOW);
        AccessToken token = new AccessToken("test-access-token", NOW.plusSeconds(300));
        given(userStorage.findByEmail(EMAIL)).willReturn(Optional.of(user));
        given(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).willReturn(true);
        given(tokenIssuer.issue(USER_ID, role)).willReturn(token);

        AccessToken result = authService.login(new LoginUserCommand("  OLEG@EXAMPLE.COM  ", PASSWORD));

        assertEquals(token, result);
        then(userStorage).should().findByEmail(EMAIL);
        then(passwordEncoder).should().matches(PASSWORD, PASSWORD_HASH);
        then(tokenIssuer).should().issue(USER_ID, role);
    }

    @Test
    void rejectsUnknownEmailWithoutIssuingToken() {
        given(userStorage.findByEmail(EMAIL)).willReturn(Optional.empty());

        InvalidCredentialsException exception = assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(new LoginUserCommand("  OLEG@EXAMPLE.COM  ", PASSWORD))
        );

        assertEquals("Invalid email or password", exception.getMessage());
        then(userStorage).should().findByEmail(EMAIL);
        verifyNoInteractions(tokenIssuer);
    }

    @Test
    void rejectsIncorrectPasswordWithoutIssuingToken() {
        User user = new User(USER_ID, EMAIL, PASSWORD_HASH, UserRole.USER, NOW, NOW);
        String wrongPassword = "wrong-password";
        given(userStorage.findByEmail(EMAIL)).willReturn(Optional.of(user));
        given(passwordEncoder.matches(wrongPassword, PASSWORD_HASH)).willReturn(false);

        InvalidCredentialsException exception = assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(new LoginUserCommand(EMAIL, wrongPassword))
        );

        assertEquals("Invalid email or password", exception.getMessage());
        then(passwordEncoder).should().matches(wrongPassword, PASSWORD_HASH);
        verifyNoInteractions(tokenIssuer);
    }
}
