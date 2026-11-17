package com.dataforge.authapi.auth.api.model.response;

import java.time.Instant;

public record LoginUserResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt
) {}