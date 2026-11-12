package com.dataforge.authapi.auth.api;

import com.dataforge.authapi.auth.api.model.request.RegisterUserRequest;
import com.dataforge.authapi.auth.api.model.response.RegisterUserResponse;
import com.dataforge.authapi.auth.commands.RegisterUserCommand;

import java.util.UUID;

public final class AuthControllerMapper {

    private AuthControllerMapper() {}

    public static RegisterUserCommand toCommand(RegisterUserRequest request) {
        return new RegisterUserCommand(request.email(), request.password());
    }

    public static RegisterUserResponse toResponse(UUID userId) {
        return new RegisterUserResponse(userId);
    }
}