package com.dataforge.authapi.auth.api;

import com.dataforge.authapi.auth.api.model.request.LoginUserRequest;
import com.dataforge.authapi.auth.api.model.request.RegisterUserRequest;
import com.dataforge.authapi.auth.api.model.response.LoginUserResponse;
import com.dataforge.authapi.auth.api.model.response.RegisterUserResponse;
import com.dataforge.authapi.auth.commands.LoginUserCommand;
import com.dataforge.authapi.auth.commands.RegisterUserCommand;
import com.dataforge.authapi.auth.model.AccessToken;

import java.util.UUID;

public final class AuthControllerMapper {

    private AuthControllerMapper() {}

    public static RegisterUserCommand toCommand(RegisterUserRequest request) {
        return new RegisterUserCommand(request.email(), request.password());
    }

    public static RegisterUserResponse toResponse(UUID userId) {
        return new RegisterUserResponse(userId);
    }

    public static LoginUserCommand toCommand(LoginUserRequest request) {
        return new LoginUserCommand(request.email(), request.password());
    }

    public static LoginUserResponse toResponse(AccessToken token) {
        return new LoginUserResponse(token.value(), "Bearer", token.expiresAt());
    }
}