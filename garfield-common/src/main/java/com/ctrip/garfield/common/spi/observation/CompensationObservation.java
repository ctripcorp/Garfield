package com.ctrip.garfield.common.spi.observation;

import com.ctrip.garfield.common.enums.OperationType;
import lombok.Builder;
import lombok.Data;

/**
 * Observation payload for compensation retry metrics.
 *
 * @author Trip.com Group
 */
@Data
@Builder
public class CompensationObservation {
    private String reqClassName;
    private String storageId;
    private int attempt;
    private String resultStatus;
    private Long nextDelayMs;
    private Exception exception;
    private OperationType operationType;
    private Long leaderWriteTimestamp;
}
