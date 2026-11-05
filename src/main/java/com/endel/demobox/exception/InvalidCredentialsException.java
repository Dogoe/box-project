package com.endel.demobox.exception;

/** Wrong email or password: 401 with a user-facing message. */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
