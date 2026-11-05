package com.endel.demobox.service;

import java.util.Optional;

public final class PhoneNumbers {
    private PhoneNumbers() {
    }

    /** Mexican number as 10 digits; accepts spaces, dashes, parentheses and a leading +52 / 52. */
    public static Optional<String> normalizeMexican(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("52")) {
            digits = digits.substring(2);
        }
        return digits.length() == 10 ? Optional.of(digits) : Optional.empty();
    }
}
