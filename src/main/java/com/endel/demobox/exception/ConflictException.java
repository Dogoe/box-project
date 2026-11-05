package com.endel.demobox.exception;

/** The request clashes with existing data (e.g. an email that already has an account): 409 with a user-facing message. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
