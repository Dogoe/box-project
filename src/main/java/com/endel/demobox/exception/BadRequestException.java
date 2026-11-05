package com.endel.demobox.exception;

/** Input that passed field validation but can't be accepted (unavailable kit, bad phone...): 400 with a user-facing message. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
