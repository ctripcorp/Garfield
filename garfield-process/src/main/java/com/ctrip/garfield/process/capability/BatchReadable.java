package com.ctrip.garfield.process.capability;

import com.ctrip.garfield.common.context.GarfieldContext;
import com.ctrip.garfield.common.model.BaseFailureResult;
import com.ctrip.garfield.common.model.OperationResult;

/**
 * Process capability mixin for read operations.
 *
 * @author Trip.com Group
 */
public interface BatchReadable<Req, Res, F extends BaseFailureResult> {
    OperationResult<Res> read(GarfieldContext<Req, F> ctx);
}
