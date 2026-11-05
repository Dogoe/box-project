package com.endel.demobox.exception;

/** The admin action doesn't apply to the order's current status (e.g. delivering a cancelled order): 409. */
public class InvalidOrderTransitionException extends RuntimeException {
    public InvalidOrderTransitionException(String message) {
        super(message);
    }
}
