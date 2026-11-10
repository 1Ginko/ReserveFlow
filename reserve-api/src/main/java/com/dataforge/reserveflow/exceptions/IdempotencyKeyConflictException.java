package com.dataforge.reserveflow.exceptions;

import java.util.UUID;

public class IdempotencyKeyConflictException extends RuntimeException {

    public IdempotencyKeyConflictException(UUID idempotencyKey) {
        super("Idempotency key was already used with different parameters: " + idempotencyKey);
    }
}