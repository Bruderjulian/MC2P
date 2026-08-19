package dev.mc2p.common.ratelimit;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A thread-safe token-bucket rate limiter keyed by client identity (remote IP
 * or token
 * id). Runs entirely off the main thread.
 */
public final class TokenBucketRateLimiter {

    public record Config(double tokensPerSecond, int capacity) {
    }

    private static final class Bucket {
        final AtomicReference<State> state;

        static final class State {
            final double tokens;
            final long nanos;

            State(final double tokens, final long nanos) {
                this.tokens = tokens;
                this.nanos = nanos;
            }
        }

        Bucket(final int capacity) {
            this.state = new AtomicReference<>(new State(capacity, System.nanoTime()));
        }
    }

    private final double tokensPerSecond;
    private final int capacity;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public TokenBucketRateLimiter(final Config config) {
        this.tokensPerSecond = Math.min(Math.max(config.tokensPerSecond, -1), 1000);
        this.capacity = Math.min(Math.max(config.capacity, 1), 100000);
    }

    public int capacity() {
        return capacity;
    }

    public double tokensPerSecond() {
        return tokensPerSecond;
    }

    /**
     * Attempts to consume one token for the given key.
     *
     * @return true if allowed, false if rate-limited
     */
    public boolean tryAcquire(final String key) {
        if (tokensPerSecond <= 0) {
            return true;
        }
        final Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(capacity));
        long now = System.nanoTime();
        Bucket.State current = bucket.state.get();
        double refilled = calculateRefill(current, now);
        Bucket.State updated = new Bucket.State(refilled - 1.0, now);
        while (true) {
            if (refilled < 1.0) {
                // Keep the refill time updated so a later retry has a fair chance.
                bucket.state.compareAndSet(current, new Bucket.State(refilled, now));
                return false;
            }
            if (bucket.state.compareAndSet(current, updated)) {
                return true;
            }
            current = bucket.state.get();
            now = System.nanoTime();
            refilled = calculateRefill(current, now);
            updated = new Bucket.State(refilled - 1.0, now);
        }
    }

    private double calculateRefill(final Bucket.State current, final long now) {
        return Math.min(capacity, current.tokens + ((now - current.nanos) / 1_000_000_000.0) * tokensPerSecond);
    }

    public void clear() {
        buckets.clear();
    }
}
