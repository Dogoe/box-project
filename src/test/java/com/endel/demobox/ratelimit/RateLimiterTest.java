package com.endel.demobox.ratelimit;

import com.endel.demobox.exception.InvalidCredentialsException;
import com.endel.demobox.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RateLimiterTest {

    /** A clock the test moves forward by hand. */
    private static final class ManualClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final ManualClock clock = new ManualClock();
    private final RateLimiter limiter = new RateLimiter(true, clock);

    @Test
    void blocksAfterTheLimitUntilTheWindowEnds() {
        for (int i = 0; i < RateLimit.REGISTER_PER_IP.getMaxAttempts(); i++) {
            limiter.consume(RateLimit.REGISTER_PER_IP, "1.2.3.4");
        }
        clock.advance(Duration.ofMinutes(20));

        TooManyRequestsException ex = assertThrows(TooManyRequestsException.class,
                () -> limiter.consume(RateLimit.REGISTER_PER_IP, "1.2.3.4"));
        assertEquals(Duration.ofMinutes(40), ex.getRetryAfter(), "counted from the first attempt of the window");

        // Other callers and other limits are independent.
        assertDoesNotThrow(() -> limiter.consume(RateLimit.REGISTER_PER_IP, "5.6.7.8"));
        assertDoesNotThrow(() -> limiter.consume(RateLimit.ORDER_PER_IP, "1.2.3.4"));

        clock.advance(Duration.ofMinutes(40));
        assertDoesNotThrow(() -> limiter.consume(RateLimit.REGISTER_PER_IP, "1.2.3.4"));
    }

    @Test
    void loginLocksAnEmailAfterRepeatedWrongPasswordsAndSuccessClearsIt() {
        LoginAttemptGuard guard = new LoginAttemptGuard(limiter);
        int allowed = RateLimit.LOGIN_FAILURES_PER_EMAIL.getMaxAttempts();

        for (int i = 0; i < allowed - 1; i++) {
            failLogin(guard, "juan@ejemplo.com", "10.0.0." + i);
        }
        // A success before the lock resets the count.
        assertEquals("ok", guard.attempt("juan@ejemplo.com", "10.0.1.1", () -> "ok"));
        for (int i = 0; i < allowed; i++) {
            failLogin(guard, "JUAN@ejemplo.com", "10.0.2." + i);
        }

        // Locked even with the right password and from a new IP; other emails are unaffected.
        assertThrows(TooManyRequestsException.class, () -> guard.attempt("juan@ejemplo.com", "10.0.3.1", () -> "ok"));
        assertEquals("ok", guard.attempt("ana@ejemplo.com", "10.0.3.1", () -> "ok"));

        clock.advance(RateLimit.LOGIN_FAILURES_PER_EMAIL.getWindow());
        assertEquals("ok", guard.attempt("juan@ejemplo.com", "10.0.3.1", () -> "ok"));
    }

    @Test
    void loginCapsAttemptsPerIpWhateverTheEmail() {
        LoginAttemptGuard guard = new LoginAttemptGuard(limiter);
        for (int i = 0; i < RateLimit.LOGIN_PER_IP.getMaxAttempts(); i++) {
            failLogin(guard, "user" + i + "@ejemplo.com", "1.2.3.4");
        }
        assertThrows(TooManyRequestsException.class, () -> guard.attempt("otro@ejemplo.com", "1.2.3.4", () -> "ok"));
    }

    @Test
    void disabledLimiterNeverBlocks() {
        RateLimiter off = new RateLimiter(false, clock);
        for (int i = 0; i < 100; i++) {
            off.consume(RateLimit.REGISTER_PER_IP, "1.2.3.4");
        }
    }

    private static void failLogin(LoginAttemptGuard guard, String email, String ip) {
        assertThrows(InvalidCredentialsException.class, () -> guard.attempt(email, ip, () -> {
            throw new InvalidCredentialsException("Correo o contraseña incorrectos.");
        }));
    }
}
