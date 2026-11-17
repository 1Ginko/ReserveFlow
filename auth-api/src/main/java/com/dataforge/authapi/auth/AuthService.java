package com.dataforge.authapi.auth;

import com.dataforge.authapi.auth.commands.LoginUserCommand;
import com.dataforge.authapi.auth.commands.RegisterUserCommand;
import com.dataforge.authapi.auth.model.AccessToken;

import java.util.UUID;

public interface AuthService {

    UUID register(RegisterUserCommand command);

    AccessToken login(LoginUserCommand command);
}