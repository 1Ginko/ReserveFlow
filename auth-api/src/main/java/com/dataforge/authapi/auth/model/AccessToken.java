package com.dataforge.authapi.auth.model;

import java.time.Instant;

public record AccessToken(
        String value,
        Instant expiresAt
) {}