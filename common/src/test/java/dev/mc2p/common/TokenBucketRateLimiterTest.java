package dev.mc2p.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mc2p.common.ratelimit.TokenBucketRateLimiter;
import org.junit.jupiter.api.Test;

class TokenBucketRateLimiterTest {

    @Test
    void burstThenLimit() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(new TokenBucketRateLimiter.Config(1.0, 2));
        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("b"));
    }

    @Test
    void keysAreIndependent() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(new TokenBucketRateLimiter.Config(1.0, 1));
        assertTrue(limiter.tryAcquire("x"));
        assertFalse(limiter.tryAcquire("x"));
        assertTrue(limiter.tryAcquire("y"));
    }

    @Test
    void nullConfigAlwaysAllows() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(null);
        assertTrue(limiter.tryAcquire("any"));
    }

    @Test
    void clearResetsBuckets() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(new TokenBucketRateLimiter.Config(1.0, 1));
        assertTrue(limiter.tryAcquire("k"));
        assertFalse(limiter.tryAcquire("k"));
        limiter.clear();
        assertTrue(limiter.tryAcquire("k"));
    }
}
