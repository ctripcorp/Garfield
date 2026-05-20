package com.ctrip.garfield.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Engine-level operation outcome codes mapped to caller-facing {@link ErrorCode}s.
 *
 * @author Trip.com Group
 */
@Getter
@RequiredArgsConstructor
public enum EngineResultCode {
    SUCCESS(0),
    ERROR(1),
    CAS_ROLLBACK_ERROR(2),
    RATE_LIMIT_ERROR(3);

    private final int value;

    /**
     * Maps an engine-level operation status to a caller-facing {@link ErrorCode}.
     *
     * <p>ERROR is mapped based on the read/write nature of the operation:
     * write operations → WRITE_LEADER_FAILURE, read operations → READ_FAILURE.
     *
     * @param operationType the current operation type, used to distinguish read vs. write semantics
     */
    public ErrorCode toErrorCode(OperationType operationType) {
        return switch (this) {
            case SUCCESS -> ErrorCode.SUCCESS;
            case ERROR -> operationType.isRead()
                    ? ErrorCode.READ_FAILURE
                    : ErrorCode.WRITE_LEADER_FAILURE;
            case CAS_ROLLBACK_ERROR -> ErrorCode.CAS_CONFLICT;
            case RATE_LIMIT_ERROR -> ErrorCode.RATE_LIMITED;
        };
    }
}
