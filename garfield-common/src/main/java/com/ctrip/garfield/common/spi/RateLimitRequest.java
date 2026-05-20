package com.ctrip.garfield.common.spi;

import com.ctrip.garfield.common.enums.OperationType;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Immutable request descriptor passed to {@link RateLimiter#tryAcquire}.
 *
 * @author Trip.com Group
 */
@Getter
@AllArgsConstructor
public class RateLimitRequest {
    private final String reqClassName;
    private final OperationType operationType;
    private final String storageId;
    private final int operationCount;
    private final String rateLimitKey;
}
