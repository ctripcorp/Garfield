package com.ctrip.garfield.common.spi.observation;

import com.ctrip.garfield.common.enums.OperationType;
import com.ctrip.garfield.common.enums.StorageType;
import com.ctrip.garfield.common.model.OperationResult;
import lombok.Builder;
import lombok.Data;

/**
 * Observation payload for write operation metrics.
 *
 * @author Trip.com Group
 */
@Data
@Builder
public class WriteObservation {
    private String reqClassName;
    private String engineId;
    private StorageType storageType;
    private OperationType operationType;
    private OperationResult<?> operationResult;
    private int totalCount;
    private long costMs;
    private boolean leader;
    private int retryAttempts;
    private Long lagMs;
}
