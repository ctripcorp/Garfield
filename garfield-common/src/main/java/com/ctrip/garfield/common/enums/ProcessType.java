package com.ctrip.garfield.common.enums;

/**
 * Data access patterns supported by storage engines.
 *
 * @author Trip.com Group
 */
public enum ProcessType {
    KV,
    HASH,
    MESSAGE,
    SERVICE_CALL,
    TOUCH,
    SCAN,
    QUERY
}
