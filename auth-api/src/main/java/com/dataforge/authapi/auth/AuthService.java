package com.dataforge.authapi.auth;

import com.dataforge.authapi.auth.commands.RegisterUserCommand;

import java.util.UUID;

public interface AuthService {

    UUID register(RegisterUserCommand command);
}