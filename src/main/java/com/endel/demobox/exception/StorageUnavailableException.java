package com.endel.demobox.exception;

/** Photo storage isn't configured or couldn't be reached. */
public class StorageUnavailableException extends RuntimeException {
    public StorageUnavailableException(String message) {
        super(message);
    }
}
