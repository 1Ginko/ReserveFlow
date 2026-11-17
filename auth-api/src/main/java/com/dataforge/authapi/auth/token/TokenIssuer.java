package com.dataforge.authapi.auth.token;

import com.dataforge.authapi.auth.model.AccessToken;
import com.dataforge.authapi.auth.model.UserRole;

import java.util.UUID;

public interface TokenIssuer {

    AccessToken issue(UUID userId, UserRole role);
}