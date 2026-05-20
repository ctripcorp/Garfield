package com.ctrip.garfield.engine.capability;

import com.ctrip.garfield.common.model.OperationResult;
import com.ctrip.garfield.engine.wrapper.HashValueWrapper;

import java.util.List;

/**
 * Engine capability for hash-based key-value operations (e.g. Redis HSET/HGET).
 * Engines that support {@link com.ctrip.garfield.common.enums.ProcessType#HASH} must implement this.
 *
 * @author Trip.com Group
 */
public interface HashCapable<T extends HashValueWrapper> {

    OperationResult<T> hashGet(List<T> wrappers, String commandKey);

    OperationResult<?> hashPut(List<T> wrappers, String commandKey);

    OperationResult<T> hashGetAll(List<T> wrappers, String commandKey);

    OperationResult<?> hashDelete(List<T> wrappers, String commandKey);
}
