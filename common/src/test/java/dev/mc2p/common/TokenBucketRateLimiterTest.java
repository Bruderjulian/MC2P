package dev.mc2p.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mc2p.common.ratelimit.TokenBucketRateLimiter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
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

    @Test
    void zeroOrNegativeRateAlwaysAllows() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(new TokenBucketRateLimiter.Config(0.0, 5));
        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("a"));
        TokenBucketRateLimiter negative = new TokenBucketRateLimiter(new TokenBucketRateLimiter.Config(-1.0, 5));
        assertTrue(negative.tryAcquire("a"));
    }

    @Test
    void configAccessorReturnsConfigured() {
        TokenBucketRateLimiter.Config config = new TokenBucketRateLimiter.Config(2.5, 7);
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(config);
        assertEquals(config, limiter.config());
        assertEquals(2.5, limiter.config().tokensPerSecond());
        assertEquals(7, limiter.config().burst());
    }

    @Test
    void tokensRefillOverTime() throws Exception {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(new TokenBucketRateLimiter.Config(10.0, 1));
        assertTrue(limiter.tryAcquire("k"));
        assertFalse(limiter.tryAcquire("k"));
        Thread.sleep(150);
        assertTrue(limiter.tryAcquire("k"));
    }

    @Test
    void burstCapacityAccumulates() throws Exception {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(new TokenBucketRateLimiter.Config(100.0, 5));
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire("k"));
        }
        assertFalse(limiter.tryAcquire("k"));
        Thread.sleep(110);
        assertTrue(limiter.tryAcquire("k"));
    }

    @Test
    void concurrentAcquiresExerciseCasRetry() throws Exception {
        int threads = 16;
        int attemptsPerThread = 500;
        TokenBucketRateLimiter limiter =
                new TokenBucketRateLimiter(new TokenBucketRateLimiter.Config(10_000.0, threads * attemptsPerThread));
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger accepted = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            Thread t = new Thread(() -> {
                try {
                    start.await();
                    for (int j = 0; j < attemptsPerThread; j++) {
                        if (limiter.tryAcquire("contended")) {
                            accepted.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
            t.start();
        }
        start.countDown();
        done.await();
        assertTrue(accepted.get() >= threads * attemptsPerThread);
    }
}
