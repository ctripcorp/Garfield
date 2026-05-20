package com.ctrip.garfield.example.model;

import com.ctrip.garfield.common.model.BaseFailureResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Example per-item failure result for order operations.
 *
 * @author Trip.com Group
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderFailureResult extends BaseFailureResult {
    private String orderId;
}
