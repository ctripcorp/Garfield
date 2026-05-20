package com.ctrip.garfield.common.spi.defaults;

import com.ctrip.garfield.common.spi.BackoffStrategy;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Exponential backoff with random jitter.
 *
 * <p>Default parameters: 2s initial, 2x multiplier, ~45h max, +/-10% jitter, 16 retries.
 * Delay formula: {@code min(initialInterval * multiplier^(attempt-1), maxInterval) +/- jitter}.
 *
 * @author Trip.com Group
 */
public class ExponentialBackoffStrategy implements BackoffStrategy {

    private static final long DEFAULT_INITIAL_INTERVAL_MS = 2000;
    private static final double DEFAULT_MULTIPLIER = 2.0;
    private static final long DEFAULT_MAX_INTERVAL_MS = 163_840_000;
    private static final double DEFAULT_JITTER_FACTOR = 0.1;
    private static final int DEFAULT_MAX_RETRIES = 16;

    private final long initialIntervalMs;
    private final double multiplier;
    private final long maxIntervalMs;
    private final double jitterFactor;
    private final int maxRetries;

    public ExponentialBackoffStrategy(long initialIntervalMs, double multiplier,
                                      long maxIntervalMs, double jitterFactor, int maxRetries) {
        if (initialIntervalMs <= 0) {
            throw new IllegalArgumentException("initialIntervalMs must be positive, got " + initialIntervalMs);
        }
        if (multiplier <= 0) {
            throw new IllegalArgumentException("multiplier must be positive, got " + multiplier);
        }
        if (maxIntervalMs <= 0) {
            throw new IllegalArgumentException("maxIntervalMs must be positive, got " + maxIntervalMs);
        }
        if (jitterFactor < 0 || jitterFactor > 1) {
            throw new IllegalArgumentException("jitterFactor must be in [0, 1], got " + jitterFactor);
        }
        if (maxRetries < -1) {
            throw new IllegalArgumentException("maxRetries must be >= -1, got " + maxRetries);
        }
        this.initialIntervalMs = initialIntervalMs;
        this.multiplier = multiplier;
        this.maxIntervalMs = maxIntervalMs;
        this.jitterFactor = jitterFactor;
        this.maxRetries = maxRetries;
    }

    public ExponentialBackoffStrategy() {
        this(DEFAULT_INITIAL_INTERVAL_MS, DEFAULT_MULTIPLIER, DEFAULT_MAX_INTERVAL_MS,
                DEFAULT_JITTER_FACTOR, DEFAULT_MAX_RETRIES);
    }

    @Override
    public long computeDelay(int attempt) {
        int index = Math.min(attempt - 1, 62); // cap to avoid long overflow with Math.pow
        long baseDelay = (long) (initialIntervalMs * Math.pow(multiplier, index));
        if (baseDelay <= 0) {
            baseDelay = maxIntervalMs;
        } else {
            baseDelay = Math.min(baseDelay, maxIntervalMs);
        }
        if (jitterFactor > 0) {
            long jitter = (long) (baseDelay * jitterFactor
                    * ThreadLocalRandom.current().nextDouble(-1, 1));
            return Math.max(0, baseDelay + jitter);
        }
        return baseDelay;
    }

    @Override
    public int maxRetries() {
        return maxRetries;
    }
}
