package com.ctrip.garfield.common.spi.defaults;

import com.ctrip.garfield.common.model.CompensationMessage;
import com.ctrip.garfield.common.spi.CompensationExhaustionHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * Default exhaustion handler that logs an error when retries are exhausted.
 *
 * @author Trip.com Group
 */
@Slf4j
public class LoggingExhaustionHandler implements CompensationExhaustionHandler {

    @Override
    public void onExhausted(CompensationMessage message, int totalAttempts, Exception lastError) {
        log.error("Compensation exhausted after {} attempts for req={} storage={}",
                totalAttempts, message.getReqClassName(), message.getStorageId(), lastError);
    }
}
