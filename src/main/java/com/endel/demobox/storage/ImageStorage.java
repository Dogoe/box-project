package com.endel.demobox.storage;

/** Stores a finished image file and returns the public URL it is served from. */
public interface ImageStorage {

    boolean isConfigured();

    String store(String key, byte[] content, String contentType);
}
