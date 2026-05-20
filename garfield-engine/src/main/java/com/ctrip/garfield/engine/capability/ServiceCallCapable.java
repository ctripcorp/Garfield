package com.ctrip.garfield.engine.capability;

import com.ctrip.garfield.common.model.OperationResult;

/**
 * Engine capability for RPC/service call operations (e.g. gRPC, HTTP).
 * Engines that support {@link com.ctrip.garfield.common.enums.ProcessType#SERVICE_CALL} must implement this.
 *
 * @param <T> request wrapper type that the engine knows how to invoke
 * @author Trip.com Group
 */
public interface ServiceCallCapable<T> {

    OperationResult<?> invoke(T requestWrapper, String commandKey);
}
