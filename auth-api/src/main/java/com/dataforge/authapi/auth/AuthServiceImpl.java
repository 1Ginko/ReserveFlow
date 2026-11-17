package com.dataforge.authapi.auth;

import com.dataforge.authapi.auth.commands.LoginUserCommand;
import com.dataforge.authapi.auth.commands.RegisterUserCommand;
import com.dataforge.authapi.auth.model.AccessToken;
import com.dataforge.authapi.auth.model.User;
import com.dataforge.authapi.auth.storage.UserStorage;
import com.dataforge.authapi.auth.token.TokenIssuer;
import com.dataforge.authapi.exceptions.InvalidCredentialsException;
import com.dataforge.authapi.exceptions.UserAlreadyExistsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserStorage userStorage;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final TokenIssuer tokenIssuer;

    public AuthServiceImpl(
            UserStorage userStorage,
            PasswordEncoder passwordEncoder,
            TokenIssuer tokenIssuer,
            Clock clock
    ) {
        this.userStorage = userStorage;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.clock = clock;
    }

    @Override
    public UUID register(RegisterUserCommand command) {
        String email = command.email().trim().toLowerCase(Locale.ROOT);
        String passwordHash = passwordEncoder.encode(command.password());

        UUID userId = UUID.randomUUID();
        Instant createdAt = clock.instant();

        boolean created = userStorage.create(userId, email, passwordHash, createdAt);
        if (!created) {
            throw new UserAlreadyExistsException(email);
        }

        return userId;
    }

    @Override
    public AccessToken login(LoginUserCommand command) {
        String email = command.email().trim().toLowerCase(Locale.ROOT);
        User user = userStorage.findByEmail(email).orElseThrow(InvalidCredentialsException::new);

        boolean passwordMatches = passwordEncoder.matches(command.password(), user.passwordHash());
        if (!passwordMatches) {
            throw new InvalidCredentialsException();
        }

        return tokenIssuer.issue(user.userId(), user.role());
    }
}