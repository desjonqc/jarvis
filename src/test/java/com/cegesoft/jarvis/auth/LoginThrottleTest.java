package com.cegesoft.jarvis.auth;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginThrottleTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final LoginThrottle throttle = new LoginThrottle(now::get);

    @Test
    void theFirstFailuresAreFree() {
        for (int i = 0; i < LoginThrottle.FREE_ATTEMPTS - 1; i++)
            throttle.onFailure("clem");
        assertEquals(0, throttle.retryAfterMs("clem"));
    }

    @Test
    void theDelayDoublesAfterEachNewFailure() {
        for (int i = 0; i < LoginThrottle.FREE_ATTEMPTS; i++)
            throttle.onFailure("clem");
        assertEquals(30_000, throttle.retryAfterMs("clem"));
        now.addAndGet(30_000);
        assertEquals(0, throttle.retryAfterMs("clem"));
        throttle.onFailure("clem");
        assertEquals(60_000, throttle.retryAfterMs("clem"));
        assertEquals(0, throttle.retryAfterMs("someone else"));
    }

    @Test
    void successAndTimeResetTheCounter() {
        for (int i = 0; i < 20; i++)
            throttle.onFailure("clem");
        assertTrue(throttle.retryAfterMs("clem") <= 15 * 60_000, "capped");
        now.addAndGet(31 * 60_000);
        assertEquals(0, throttle.retryAfterMs("clem"));

        for (int i = 0; i < LoginThrottle.FREE_ATTEMPTS; i++)
            throttle.onFailure("clem");
        throttle.onSuccess("clem");
        assertEquals(0, throttle.retryAfterMs("clem"));
    }
}
