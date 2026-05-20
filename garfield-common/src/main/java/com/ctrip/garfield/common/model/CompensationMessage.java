package com.ctrip.garfield.common.model;

import com.ctrip.garfield.common.enums.OperationType;
import lombok.Builder;
import lombok.Data;

/**
 * Message published to {@code CompensationChannel} when a follower write fails.
 * Contains all information needed to replay the write on the consumer side.
 *
 * @author Trip.com Group
 */
@Data
@Builder
public class CompensationMessage {
    private String reqClassName;
    /** Serialized request data list (via GarfieldSerializer). */
    private String requestData;
    /** Serialized error details from the failed write, may be null. */
    private String errorDetails;
    /** Target storage engine ID that failed. */
    private String storageId;
    private String traceId;
    private OperationType operationType;
    private Long expireAtMs;
    private Long leaderWriteTimestamp;
}
