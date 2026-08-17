package dev.mc2p.common.ratelimit;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A thread-safe token-bucket rate limiter keyed by client identity (remote IP or token
 * id). Runs entirely off the main thread.
 */
public final class TokenBucketRateLimiter {

    public record Config(double tokensPerSecond, int burst) {
    }

    private static final class Bucket {
        final double rate;
        final double capacity;
        final AtomicReference<State> state;

        static final class State {
            final double tokens;
            final long nanos;

            State(double tokens, long nanos) {
                this.tokens = tokens;
                this.nanos = nanos;
            }
        }

        Bucket(double rate, double capacity) {
            this.rate = rate;
            this.capacity = capacity;
            this.state = new AtomicReference<>(new State(capacity, System.nanoTime()));
        }
    }

    private final Config config;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public TokenBucketRateLimiter(Config config) {
        this.config = config;
    }

    public Config config() {
        return config;
    }

    /**
     * Attempts to consume one token for the given key.
     *
     * @return true if allowed, false if rate-limited
     */
    public boolean tryAcquire(String key) {
        if (config == null || config.tokensPerSecond() <= 0) {
            return true;
        }
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(config.tokensPerSecond(), config.burst()));
        long now = System.nanoTime();
        Bucket.State current = bucket.state.get();
        double elapsedSec = (now - current.nanos) / 1_000_000_000.0;
        double refilled = Math.min(bucket.capacity, current.tokens + elapsedSec * bucket.rate);
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
            elapsedSec = (now - current.nanos) / 1_000_000_000.0;
            refilled = Math.min(bucket.capacity, current.tokens + elapsedSec * bucket.rate);
            updated = new Bucket.State(refilled - 1.0, now);
        }
    }

    public void clear() {
        buckets.clear();
    }
}