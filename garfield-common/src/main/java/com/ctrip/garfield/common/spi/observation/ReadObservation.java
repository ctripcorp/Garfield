package com.ctrip.garfield.common.spi.observation;

import com.ctrip.garfield.common.enums.OperationType;
import com.ctrip.garfield.common.enums.StorageType;
import com.ctrip.garfield.common.enums.EngineResultCode;
import lombok.Builder;
import lombok.Data;

/**
 * Observation payload for read operation metrics.
 *
 * @author Trip.com Group
 */
@Data
@Builder
public class ReadObservation {
    private String reqClassName;
    private String engineId;
    private StorageType storageType;
    private OperationType operationType;
    private EngineResultCode resultCode;
    private int count;
    private long costMs;
    private Exception exception;
}
