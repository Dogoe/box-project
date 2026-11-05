package com.endel.demobox.exception;

import java.time.Duration;

/** A rate limit was hit; {@code retryAfter} is how long until the caller may try again. */
public class TooManyRequestsException extends RuntimeException {
    private final Duration retryAfter;

    public TooManyRequestsException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
