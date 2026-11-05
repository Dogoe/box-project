package com.endel.demobox.ratelimit;

import com.endel.demobox.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory fixed-window counters keyed by limit + caller (IP or email).
 *
 * <p>Kept in memory on purpose: one app instance, no extra infrastructure, and a restart only
 * forgives the attempts in progress. Running several instances would need a shared store instead.
 */
@Component
public class RateLimiter {
    /** Above this many tracked keys, expired ones are dropped on the next write. */
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final boolean enabled;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Autowired
    public RateLimiter(@Value("${app.rate-limit.enabled:true}") boolean enabled) {
        this(enabled, Clock.systemUTC());
    }

    RateLimiter(boolean enabled, Clock clock) {
        this.enabled = enabled;
        this.clock = clock;
    }

    /** Counts one attempt, or throws if the limit was already reached. */
    public void consume(RateLimit limit, String key) {
        check(limit, key);
        record(limit, key);
    }

    /** Throws if the limit was already reached, without counting this call. */
    public void check(RateLimit limit, String key) {
        if (!enabled) {
            return;
        }
        Window window = windows.get(id(limit, key));
        Instant now = clock.instant();
        if (window != null && !window.expired(now) && window.count >= limit.getMaxAttempts()) {
            throw new TooManyRequestsException(limit.getMessage(), Duration.between(now, window.end));
        }
    }

    /** Counts one attempt (e.g. a failed login) without checking. */
    public void record(RateLimit limit, String key) {
        if (!enabled) {
            return;
        }
        Instant now = clock.instant();
        windows.compute(id(limit, key), (id, window) -> {
            if (window == null || window.expired(now)) {
                return new Window(now.plus(limit.getWindow()), 1);
            }
            return new Window(window.end, window.count + 1);
        });
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(window -> window.expired(now));
        }
    }

    public void reset(RateLimit limit, String key) {
        windows.remove(id(limit, key));
    }

    private static String id(RateLimit limit, String key) {
        return limit.name() + '|' + (key == null ? "" : key.trim().toLowerCase());
    }

    private record Window(Instant end, int count) {
        boolean expired(Instant now) {
            return !now.isBefore(end);
        }
    }
}
