package com.ctrip.garfield.common.lock;

import lombok.Data;

/**
 * Metadata holder for a distributed lock request and its outcome.
 *
 * @author Trip.com Group
 */
@Data
public class LockEntity {
    private String key;
    private String token;
    private volatile boolean locked;
    /**
     * Retry count for lock acquisition.
     * <p>Any non-positive value (including the Java primitive default {@code 0}
     * and the explicit sentinel {@code -1}) means "use implementation default".
     */
    private int retryCount;
    /**
     * Lock expiration in milliseconds.
     * <p>Any non-positive value (including the Java primitive default {@code 0}
     * and the explicit sentinel {@code -1}) means "use implementation default".
     */
    private long expireMs;
}
