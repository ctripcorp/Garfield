package com.ctrip.garfield.process.capability;

import com.ctrip.garfield.common.context.GarfieldContext;
import com.ctrip.garfield.common.model.BaseFailureResult;
import com.ctrip.garfield.common.model.OperationResult;

/**
 * Process capability mixin for write operations.
 *
 * @author Trip.com Group
 */
public interface BatchWritable<Req, F extends BaseFailureResult> {
    OperationResult<?> write(GarfieldContext<Req, F> ctx);
}
