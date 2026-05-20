package com.ctrip.garfield.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Framework-wide error codes for result classification.
 *
 * @author Trip.com Group
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    SUCCESS(0, "Success"),
    ROUTE_NOT_FOUND(1001, "Storage route not found"),
    WRITE_LEADER_FAILURE(2001, "Write to leader storage failed"),
    WRITE_FOLLOWER_FAILURE(2002, "Write to follower storage failed"),
    CAS_CONFLICT(2003, "CAS version conflict"),
    RATE_LIMITED(2004, "Rate limited"),
    READ_FAILURE(3001, "Read from storage failed"),
    LOCK_CHECK_FAILURE(4001, "Lock check failed"),
    LOCK_ACQUIRE_FAILURE(4002, "Lock acquire failed"),
    LOCK_RELEASE_FAILURE(4003, "Lock release failed"),
    UNSUPPORTED_OPERATION(5001, "Operation not supported by this storage process"),
    SERIALIZATION_FAILED(5002, "Serialization / deserialization failed"),
    UNKNOWN_ERROR(9999, "Unknown error");

    private final int code;
    private final String message;
}
