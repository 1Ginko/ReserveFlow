package com.dataforge.authapi.auth.commands;

public record RegisterUserCommand(
        String email,
        String password
) {}