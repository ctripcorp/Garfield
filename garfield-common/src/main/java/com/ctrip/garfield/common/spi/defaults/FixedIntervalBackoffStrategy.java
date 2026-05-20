package com.ctrip.garfield.common.spi.defaults;

import com.ctrip.garfield.common.spi.BackoffStrategy;

/**
 * Fixed-interval backoff strategy for compensation retries.
 *
 * @author Trip.com Group
 */
public class FixedIntervalBackoffStrategy implements BackoffStrategy {

    private static final long DEFAULT_INTERVAL_MS = 1000;
    private static final int DEFAULT_MAX_RETRIES = 3;

    private final long intervalMs;
    private final int maxRetries;

    public FixedIntervalBackoffStrategy(long intervalMs, int maxRetries) {
        if (intervalMs <= 0) {
            throw new IllegalArgumentException("intervalMs must be positive, got " + intervalMs);
        }
        if (maxRetries < -1) {
            throw new IllegalArgumentException("maxRetries must be >= -1, got " + maxRetries);
        }
        this.intervalMs = intervalMs;
        this.maxRetries = maxRetries;
    }

    public FixedIntervalBackoffStrategy() {
        this(DEFAULT_INTERVAL_MS, DEFAULT_MAX_RETRIES);
    }

    @Override
    public long computeDelay(int attempt) {
        return intervalMs;
    }

    @Override
    public int maxRetries() {
        return maxRetries;
    }
}
