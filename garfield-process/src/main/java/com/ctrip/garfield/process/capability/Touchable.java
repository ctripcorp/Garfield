package com.ctrip.garfield.process.capability;

import com.ctrip.garfield.common.context.GarfieldContext;
import com.ctrip.garfield.common.model.BaseFailureResult;
import com.ctrip.garfield.common.model.OperationResult;

/**
 * Process capability mixin for expiration refresh operations.
 *
 * @author Trip.com Group
 */
public interface Touchable<Req, F extends BaseFailureResult> {
    OperationResult<?> touch(GarfieldContext<Req, F> ctx, long expireAtMs);
}
