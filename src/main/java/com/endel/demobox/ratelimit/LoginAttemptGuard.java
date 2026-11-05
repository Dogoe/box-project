package com.endel.demobox.ratelimit;

import com.endel.demobox.exception.InvalidCredentialsException;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Wraps a login (customer or admin): caps attempts per IP, and locks an email for a while after
 * repeated wrong passwords. A successful login clears that email's failures.
 */
@Component
public class LoginAttemptGuard {
    private final RateLimiter rateLimiter;

    public LoginAttemptGuard(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    /** {@code login} must throw {@link InvalidCredentialsException} for a wrong email or password. */
    public <T> T attempt(String email, String clientIp, Supplier<T> login) {
        rateLimiter.consume(RateLimit.LOGIN_PER_IP, clientIp);
        rateLimiter.check(RateLimit.LOGIN_FAILURES_PER_EMAIL, email);
        try {
            T result = login.get();
            rateLimiter.reset(RateLimit.LOGIN_FAILURES_PER_EMAIL, email);
            return result;
        } catch (InvalidCredentialsException e) {
            rateLimiter.record(RateLimit.LOGIN_FAILURES_PER_EMAIL, email);
            throw e;
        }
    }
}
