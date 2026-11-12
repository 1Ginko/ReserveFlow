package com.dataforge.authapi.auth.api;

import com.dataforge.authapi.auth.AuthService;
import com.dataforge.authapi.auth.api.model.request.RegisterUserRequest;
import com.dataforge.authapi.auth.api.model.response.RegisterUserResponse;
import com.dataforge.authapi.auth.commands.RegisterUserCommand;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterUserResponse register(@Valid @RequestBody RegisterUserRequest request) {
        RegisterUserCommand command = AuthControllerMapper.toCommand(request);
        UUID userId = authService.register(command);
        return AuthControllerMapper.toResponse(userId);
    }
}