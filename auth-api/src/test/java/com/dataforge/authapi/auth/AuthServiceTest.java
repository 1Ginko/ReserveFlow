package com.dataforge.authapi.auth;

import com.dataforge.authapi.auth.commands.RegisterUserCommand;
import com.dataforge.authapi.auth.storage.UserStorage;
import com.dataforge.authapi.exceptions.UserAlreadyExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String EMAIL = "oleg@example.com";
    private static final String PASSWORD = " my-long-password-123 ";
    private static final String PASSWORD_HASH = "{bcrypt}test-hash";
    private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");

    @Mock
    private UserStorage userStorage;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                userStorage,
                passwordEncoder,
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
}
