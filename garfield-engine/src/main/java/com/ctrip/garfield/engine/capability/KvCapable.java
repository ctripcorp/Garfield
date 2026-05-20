package com.ctrip.garfield.engine.capability;

import com.ctrip.garfield.common.model.OperationResult;
import com.ctrip.garfield.engine.wrapper.KvValueWrapper;

import java.util.List;

/**
 * Engine capability for key-value operations. Engines that support
 * {@link com.ctrip.garfield.common.enums.ProcessType#KV} must implement this.
 *
 * <p>refreshTTL has been split into {@link TouchCapable} (semantics narrowed to EXPIREAT);
 * query has been split into {@link QueryCapable}.
 *
 * @param <T> the value wrapper type carrying key, value, version, and lock info
 * @author Trip.com Group
 */
public interface KvCapable<T extends KvValueWrapper> {

    OperationResult<T> batchGet(List<T> wrappers, String commandKey);

    OperationResult<?> batchPut(List<T> wrappers, String commandKey);

    /**
     * Batch delete by key.
     *
     * <p>Idempotent — deleting a non-existent key is not an error.
     * <p>{@code OperationResult.actualExecuteSize} must be set to {@code wrappers.size()}
     * (input count), not the actual number of keys deleted (Plan B, see spec Open Question #2).
     */
    OperationResult<?> batchDelete(List<T> wrappers, String commandKey);
}
