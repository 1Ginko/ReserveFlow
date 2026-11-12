package com.dataforge.authapi.auth;

import com.dataforge.authapi.auth.commands.RegisterUserCommand;
import com.dataforge.authapi.auth.model.User;
import com.dataforge.authapi.auth.model.UserRole;
import com.dataforge.authapi.auth.storage.UserStorage;
import com.dataforge.authapi.config.PostgresTestConfiguration;
import com.dataforge.authapi.data.entity.UserEntity;
import com.dataforge.authapi.data.repository.UserRepository;
import com.dataforge.authapi.exceptions.UserAlreadyExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class AuthRegistrationIntegrationTest {

    private static final String EMAIL = "oleg@example.com";
    private static final String PASSWORD = "my-long-password-123";
    private static final String SECOND_PASSWORD = "another-long-password-456";
    private static final Instant NOW = Instant.parse("2026-11-12T10:00:00.123456Z");

    @Autowired
    private AuthService authService;

    @Autowired
    private UserStorage userStorage;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        userRepository.deleteAllInBatch();
        given(clock.instant()).willReturn(NOW);
    }

    @Test
    void registersAndFindsUserWithNormalizedEmailEncodedPasswordAndDefaultRole() {
        UUID userId = authService.register(new RegisterUserCommand("  OLEG@EXAMPLE.COM  ", PASSWORD));

        User user = userStorage.findByEmail(EMAIL).orElseThrow();
        assertEquals(userId, user.userId());
        assertEquals(EMAIL, user.email());
        assertEquals(UserRole.USER, user.role());
        assertEquals(NOW, user.createdAt());
        assertEquals(NOW, user.updatedAt());
        assertTrue(user.passwordHash().startsWith("{bcrypt}"));
        assertTrue(passwordEncoder.matches(PASSWORD, user.passwordHash()));
        assertEquals(1L, userRepository.count());
        assertTrue(userStorage.findByEmail("missing@example.com").isEmpty());
    }

    @Test
    void duplicateEmailDoesNotOverwriteExistingUser() {
        given(clock.instant()).willReturn(NOW, NOW.plusSeconds(60));
        UUID userId = authService.register(new RegisterUserCommand(EMAIL, PASSWORD));

        UserAlreadyExistsException exception = assertThrows(
                UserAlreadyExistsException.class,
                () -> authService.register(new RegisterUserCommand("OLEG@EXAMPLE.COM", SECOND_PASSWORD))
        );

        assertEquals("User with email already exists: " + EMAIL, exception.getMessage());
        assertEquals(1L, userRepository.count());

        UserEntity stored = userRepository.findByEmail(EMAIL).orElseThrow();
        assertEquals(userId, stored.getUserId());
        assertEquals(UserRole.USER, stored.getRole());
        assertEquals(NOW, stored.getCreatedAt());
        assertEquals(NOW, stored.getUpdatedAt());
        assertTrue(passwordEncoder.matches(PASSWORD, stored.getPasswordHash()));
        assertFalse(passwordEncoder.matches(SECOND_PASSWORD, stored.getPasswordHash()));
    }

    @Test
    void concurrentRegistrationsWaitForCommitAndCreateOnlyOneUser() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CompletableFuture<Void> firstInserted = new CompletableFuture<>();
        CompletableFuture<Void> allowCommit = new CompletableFuture<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            try {
                Future<UUID> firstResult = executor.submit(() -> transaction.execute(status -> {
                    UUID userId = authService.register(new RegisterUserCommand("OLEG@EXAMPLE.COM", PASSWORD));
                    firstInserted.complete(null);
                    allowCommit.join();
                    return userId;
                }));

                firstInserted.get(10, TimeUnit.SECONDS);

                Future<UUID> secondResult = executor.submit(() -> authService.register(
                        new RegisterUserCommand(EMAIL, SECOND_PASSWORD)
                ));

                await().atMost(5, TimeUnit.SECONDS).until(() -> jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM pg_stat_activity
                        WHERE datname = current_database()
                          AND cardinality(pg_blocking_pids(pid)) > 0
                          AND query LIKE '%INSERT INTO users%'
                        """, Long.class) > 0);

                allowCommit.complete(null);

                UUID userId = firstResult.get(10, TimeUnit.SECONDS);
                ExecutionException exception = assertThrows(
                        ExecutionException.class,
                        () -> secondResult.get(10, TimeUnit.SECONDS)
                );

                assertInstanceOf(UserAlreadyExistsException.class, exception.getCause());
                assertEquals(1L, userRepository.count());

                UserEntity stored = userRepository.findByEmail(EMAIL).orElseThrow();
                assertEquals(userId, stored.getUserId());
                assertTrue(passwordEncoder.matches(PASSWORD, stored.getPasswordHash()));
                assertFalse(passwordEncoder.matches(SECOND_PASSWORD, stored.getPasswordHash()));
            } finally {
                allowCommit.complete(null);
            }
        }
    }
}
