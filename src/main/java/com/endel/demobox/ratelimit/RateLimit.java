package com.endel.demobox.ratelimit;

import java.time.Duration;

/**
 * Abuse limits for the public endpoints. Each one allows {@code maxAttempts} within {@code window},
 * counted from the first attempt; after that the caller gets a 429 until the window ends.
 */
public enum RateLimit {
    /** Every login attempt (customer or admin) from one IP. */
    LOGIN_PER_IP(20, Duration.ofMinutes(15), "Demasiados intentos de inicio de sesión."),
    /** Failed logins for one email, from any IP; a successful login clears it. */
    LOGIN_FAILURES_PER_EMAIL(5, Duration.ofMinutes(15),
            "Demasiados intentos fallidos con este correo. Si no recuerdas tu contraseña, restablécela."),
    /** "Does this email have an account?" lookups from checkout (keeps the email list from being scraped). */
    EMAIL_STATUS_PER_IP(30, Duration.ofMinutes(10), "Demasiadas consultas."),
    REGISTER_PER_IP(5, Duration.ofHours(1), "Demasiadas cuentas creadas desde esta conexión."),
    /** Password-reset and verification emails to one address (so nobody can flood an inbox). */
    EMAIL_LINK_PER_EMAIL(3, Duration.ofHours(1), "Ya te enviamos varios correos."),
    EMAIL_LINK_PER_IP(10, Duration.ofHours(1), "Demasiadas solicitudes de correo."),
    RESET_PASSWORD_PER_IP(10, Duration.ofMinutes(15), "Demasiados intentos."),
    /** Guest orders also email whatever address is typed, so they're capped too. */
    ORDER_PER_IP(10, Duration.ofHours(1), "Demasiados pedidos desde esta conexión.");

    private final int maxAttempts;
    private final Duration window;
    private final String message;

    RateLimit(int maxAttempts, Duration window, String message) {
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.message = message;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public Duration getWindow() {
        return window;
    }

    public String getMessage() {
        return message;
    }
}
