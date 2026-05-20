package com.ctrip.garfield.example.model;

import com.ctrip.garfield.common.model.BaseDataUnit;
import com.ctrip.garfield.common.model.BaseFailureResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Example business data unit representing an order.
 *
 * @author Trip.com Group
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderDataUnit extends BaseDataUnit {
    private String orderId;
    private String productName;
    private long amount;
    private long timestamp;

    @Override
    public BaseFailureResult createFailureResult(String failureType) {
        return new BaseFailureResult(failureType);
    }
}
