package com.cegesoft.jarvis.auth;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Slows down password guessing on an account : after {@value #FREE_ATTEMPTS} failures, each new attempt has to wait
 * (30 s, then 1 min, 2 min... up to 15 min). A successful login, or 15 minutes without failure, resets the counter.
 */
public class LoginThrottle {

    static final int FREE_ATTEMPTS = 5;
    private static final long BASE_DELAY_MS = 30_000;
    private static final long MAX_DELAY_MS = 15 * 60_000;
    private static final long FORGET_AFTER_MS = 15 * 60_000;

    private record Failures(int count, long last) {
    }

    private final Map<String, Failures> failures = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public LoginThrottle() {
        this(System::currentTimeMillis);
    }

    LoginThrottle(LongSupplier clock) {
        this.clock = clock;
    }

    /**
     * @return the milliseconds to wait before the next attempt on this account (0 = allowed now)
     */
    public long retryAfterMs(String key) {
        Failures entry = failures.get(key);
        long now = clock.getAsLong();
        if (entry == null)
            return 0;
        if (now - entry.last() > FORGET_AFTER_MS + delay(entry.count())) {
            failures.remove(key, entry);
            return 0;
        }
        return Math.max(0, entry.last() + delay(entry.count()) - now);
    }

    public void onFailure(String key) {
        long now = clock.getAsLong();
        failures.merge(key, new Failures(1, now), (old, ignored) -> new Failures(old.count() + 1, now));
    }

    public void onSuccess(String key) {
        failures.remove(key);
    }

    private static long delay(int count) {
        if (count < FREE_ATTEMPTS)
            return 0;
        int exponent = Math.min(count - FREE_ATTEMPTS, 10);
        return Math.min(BASE_DELAY_MS << exponent, MAX_DELAY_MS);
    }
}
