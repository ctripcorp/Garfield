package com.ctrip.garfield.common.spi;

import com.ctrip.garfield.common.model.CompensationMessage;

/**
 * SPI for handling exhausted compensation retries.
 *
 * <p>Called when retry attempts reach {@code BackoffStrategy.maxRetries()}.
 * The default {@code LoggingExhaustionHandler} logs an error. Production
 * environments should replace it with alerting, dead-letter queue, or
 * manual intervention logic.
 *
 * @author Trip.com Group
 */
public interface CompensationExhaustionHandler {

    /**
     * Called when all retry attempts are exhausted.
     *
     * @param message       the original compensation message
     * @param totalAttempts total retry attempts performed
     * @param lastError     the last exception, or {@code null} if writeData returned false without throwing
     */
    void onExhausted(CompensationMessage message, int totalAttempts, Exception lastError);
}
