package com.ctrip.garfield.common.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-item failure detail for partial-failure write results.
 *
 * @author Trip.com Group
 */
@Data
@NoArgsConstructor
public class BaseFailureResult {
    private String failureType;
    private int dataIndex = -1;

    public BaseFailureResult(String failureType) {
        this.failureType = failureType;
    }
}
