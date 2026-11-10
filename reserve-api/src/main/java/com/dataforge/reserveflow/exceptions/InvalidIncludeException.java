package com.dataforge.reserveflow.exceptions;

public class InvalidIncludeException extends RuntimeException {

    public InvalidIncludeException(String include) {
        super("Unsupported include value: " + include);
    }
}