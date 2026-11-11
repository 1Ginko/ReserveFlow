package com.dataforge.authapi.auth.model;

import java.time.Instant;
import java.util.UUID;

public record User(
        UUID userId,
        String email,
        String passwordHash,
        UserRole role,
        Instant createdAt,
        Instant updatedAt
) {}