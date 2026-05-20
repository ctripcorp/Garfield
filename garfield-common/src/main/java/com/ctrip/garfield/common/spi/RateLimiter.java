package com.ctrip.garfield.common.spi;

/**
 * SPI for rate limiting storage engine operations.
 *
 * @author Trip.com Group
 */
public interface RateLimiter {
    boolean tryAcquire(RateLimitRequest request);
}
