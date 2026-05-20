package com.ctrip.garfield.common.enums;

/**
 * Operation types supported by the framework.
 *
 * <p>Used for metrics tagging, rate-limit keys, and compensation message serialization.
 * Orchestrators no longer dispatch via {@code switch(OperationType)};
 * capability routing is handled by the Capability Mixin mechanism.
 *
 * <ul>
 *   <li>{@link #BATCH_PUT} — batch write</li>
 *   <li>{@link #BATCH_DELETE} — batch delete</li>
 *   <li>{@link #TOUCH} — refresh expiration time</li>
 *   <li>{@link #BATCH_GET} — batch primary-key lookup</li>
 *   <li>{@link #SCAN} — full / range scan</li>
 *   <li>{@link #QUERY} — non-primary-key / secondary-index query</li>
 * </ul>
 *
 * @author Trip.com Group
 */
public enum OperationType {

    BATCH_PUT("batchPut"),
    BATCH_DELETE("batchDelete"),
    TOUCH("touch"),
    BATCH_GET("batchGet"),
    SCAN("scan"),
    QUERY("query");

    private final String tag;

    OperationType(String tag) {
        this.tag = tag;
    }

    /**
     * Returns a lowercase method-name tag for metrics keys and log identifiers.
     */
    public String tag() {
        return tag;
    }

    public boolean isRead() {
        return this == BATCH_GET || this == SCAN || this == QUERY;
    }

    public boolean isWrite() {
        return !isRead();
    }
}
