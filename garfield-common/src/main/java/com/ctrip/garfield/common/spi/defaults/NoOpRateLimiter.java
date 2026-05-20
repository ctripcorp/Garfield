package com.ctrip.garfield.common.spi.defaults;

import com.ctrip.garfield.common.spi.RateLimiter;
import com.ctrip.garfield.common.spi.RateLimitRequest;

/**
 * No-op rate limiter that always permits requests.
 *
 * @author Trip.com Group
 */
public class NoOpRateLimiter implements RateLimiter {
    @Override
    public boolean tryAcquire(RateLimitRequest request) {
        return true;
    }
}
