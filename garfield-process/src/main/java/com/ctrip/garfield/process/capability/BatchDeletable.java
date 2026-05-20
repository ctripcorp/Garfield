package com.ctrip.garfield.process.capability;

import com.ctrip.garfield.common.context.GarfieldContext;
import com.ctrip.garfield.common.model.BaseFailureResult;
import com.ctrip.garfield.common.model.OperationResult;

/**
 * Process capability mixin for delete operations.
 *
 * @author Trip.com Group
 */
public interface BatchDeletable<Req, F extends BaseFailureResult> {
    OperationResult<?> delete(GarfieldContext<Req, F> ctx);
}
