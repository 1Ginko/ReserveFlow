package com.dataforge.authapi.exceptions;

public class UserAlreadyExistsException extends RuntimeException {

    public UserAlreadyExistsException(String email) {
        super("User with email already exists: " + email);
    }
}