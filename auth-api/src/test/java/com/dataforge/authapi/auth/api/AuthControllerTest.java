package com.dataforge.authapi.auth.api;

import com.dataforge.authapi.auth.AuthService;
import com.dataforge.authapi.auth.commands.RegisterUserCommand;
import com.dataforge.authapi.config.SecurityConfig;
import com.dataforge.authapi.exceptions.UserAlreadyExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    private static final UUID USER_ID = UUID.fromString("4eeaa832-8a05-4c00-b45c-5a1c18746a41");
    private static final String EMAIL = "oleg@example.com";
    private static final String PASSWORD = "my-long-password-123";
    private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        given(clock.instant()).willReturn(NOW);
    }

    @ParameterizedTest
    @MethodSource("validPasswords")
    void registersWithoutAuthenticationAndAcceptsValidPasswords(String password) throws Exception {
        RegisterUserCommand command = new RegisterUserCommand(EMAIL, password);
        given(authService.register(command)).willReturn(USER_ID);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(requestJson(EMAIL, password)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.password").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.passwordHash").doesNotHaveJsonPath());

        then(authService).should().register(command);
    }

    @Test
    void returnsConflictForAlreadyRegisteredEmail() throws Exception {
        RegisterUserCommand command = new RegisterUserCommand(EMAIL, PASSWORD);
        given(authService.register(command)).willThrow(new UserAlreadyExistsException(EMAIL));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(requestJson(EMAIL, PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.timestamp").value(NOW.toString()))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("User with email already exists: " + EMAIL))
                .andExpect(jsonPath("$.path").value("/api/auth/register"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        then(authService).should().register(command);
    }

    @ParameterizedTest
    @ValueSource(strings = {"oleg", "oleg@gmai", "oleg@@example.com"})
    void rejectsInvalidEmail(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(requestJson(email, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").value("Email must be valid"));

        verifyNoInteractions(authService);
    }

    @Test
    void acceptsEmailAtMaximumLength() throws Exception {
        String email = longEmail(57);
        RegisterUserCommand command = new RegisterUserCommand(email, PASSWORD);
        given(authService.register(command)).willReturn(USER_ID);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(requestJson(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()));

        then(authService).should().register(command);
    }

    @Test
    void rejectsEmailLongerThanMaximum() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(requestJson(longEmail(58), PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").isString());

        verifyNoInteractions(authService);
    }

    @ParameterizedTest
    @MethodSource("shortPasswords")
    void rejectsPasswordsWithFewerThanFifteenCodePoints(String password) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(requestJson(EMAIL, password)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password")
                        .value("Password must contain at least 15 characters"));

        verifyNoInteractions(authService);
    }

    @ParameterizedTest
    @MethodSource("oversizedPasswords")
    void rejectsPasswordsExceedingSeventyTwoUtf8Bytes(String password) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(requestJson(EMAIL, password)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password")
                        .value("Password must not exceed 72 UTF-8 bytes"));

        verifyNoInteractions(authService);
    }

    @Test
    void returnsFieldErrorsForMissingFields() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").value("Email must not be blank"))
                .andExpect(jsonPath("$.fieldErrors.password").value("Password must not be blank"));

        verifyNoInteractions(authService);
    }

    @Test
    void rejectsBlankFields() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(requestJson(" ", " ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").isString())
                .andExpect(jsonPath("$.fieldErrors.password").isString());

        verifyNoInteractions(authService);
    }

    @Test
    void returnsAllInvalidFieldsInErrorResponse() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(requestJson("invalid-email", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").value(NOW.toString()))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value("/api/auth/register"))
                .andExpect(jsonPath("$.fieldErrors.email").value("Email must be valid"))
                .andExpect(jsonPath("$.fieldErrors.password")
                        .value("Password must contain at least 15 characters"));

        verifyNoInteractions(authService);
    }

    private static Stream<String> validPasswords() {
        return Stream.of(
                PASSWORD,
                "a".repeat(15),
                "a".repeat(72),
                "я".repeat(36),
                "😀".repeat(18),
                "my-long-passеещ",
                " my-long-password-123 "
        );
    }

    private static Stream<String> shortPasswords() {
        return Stream.of("short", "a".repeat(14), "😀".repeat(14));
    }

    private static Stream<String> oversizedPasswords() {
        return Stream.of("a".repeat(73), "я".repeat(37), "😀".repeat(19));
    }

    private static String longEmail(int lastLabelLength) {
        return "a".repeat(64) + "@" + "b".repeat(63) + "."
                + "c".repeat(63) + "." + "d".repeat(lastLabelLength) + ".com";
    }

    private static String requestJson(String email, String password) {
        return """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);
    }
}
