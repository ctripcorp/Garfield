package com.ctrip.garfield.common.spi;

/**
 * SPI for compensation retry backoff strategy.
 *
 * <p>Built-in implementations:
 * <ul>
 *   <li>{@code ExponentialBackoffStrategy} — exponential backoff with jitter</li>
 *   <li>{@code FixedIntervalBackoffStrategy} — fixed interval</li>
 * </ul>
 *
 * @author Trip.com Group
 */
public interface BackoffStrategy {

    /**
     * Computes the delay before the given retry attempt.
     *
     * @param attempt current retry count, starting from 1
     * @return delay in milliseconds, must not be negative
     */
    long computeDelay(int attempt);

    /**
     * Maximum number of retries allowed.
     *
     * @return positive integer for bounded retries; {@code -1} for unlimited
     */
    int maxRetries();
}
