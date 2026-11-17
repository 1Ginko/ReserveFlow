package com.dataforge.authapi.auth.commands;

public record LoginUserCommand(
        String email,
        String password
) {}