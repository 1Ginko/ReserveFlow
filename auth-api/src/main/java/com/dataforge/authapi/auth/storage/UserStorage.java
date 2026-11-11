package com.dataforge.authapi.auth.storage;

import com.dataforge.authapi.auth.model.User;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserStorage {

    boolean create(
            UUID userId,
            String email,
            String passwordHash,
            Instant createdAt
    );

    Optional<User> findByEmail(String email);
}