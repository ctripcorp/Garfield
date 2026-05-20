package com.ctrip.garfield.common.config;

import lombok.Data;

/**
 * Retry policy parameters for storage engine operations.
 *
 * @author Trip.com Group
 */
@Data
public class RetryConfig {
    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final long DEFAULT_RETRY_INTERVAL_MS = 100;

    private int maxAttempts = DEFAULT_MAX_ATTEMPTS;
    private long retryIntervalMs = DEFAULT_RETRY_INTERVAL_MS;
}
