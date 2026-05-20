package com.ctrip.garfield.transfer;

import com.ctrip.garfield.common.context.GarfieldContext;
import com.ctrip.garfield.common.model.BaseFailureResult;

/**
 * Converts a {@link GarfieldContext} into a service request wrapper.
 * The actual invocation is performed by the Engine (via {@code ServiceCallCapable.invoke}).
 *
 * @param <ReqData>     business request data type
 * @param <FailureType> per-item failure detail type
 * @param <W>           request wrapper type that the Engine accepts
 * @author Trip.com Group
 */
public interface ServiceTransfer<ReqData, FailureType extends BaseFailureResult, W> {

    W toServiceRequest(GarfieldContext<ReqData, FailureType> ctx);

    default boolean filterBeforePut(GarfieldContext<ReqData, FailureType> ctx) {
        return true;
    }
}
