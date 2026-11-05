package com.endel.demobox.service.account;

/**
 * Carries the raw link token in memory only (the database keeps just its hash), so the email can be
 * sent after the transaction that created the token commits.
 */
public record AccountEmailEvent(Type type, String email, String fullName, String rawToken) {
    public enum Type {
        VERIFY_EMAIL,
        RESET_PASSWORD
    }
}
