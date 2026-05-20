package com.ctrip.garfield.engine.capability;

import com.ctrip.garfield.common.model.OperationResult;

import java.util.List;

/**
 * Engine-layer rare capability — refresh expiration time (EXPIREAT semantics).
 *
 * <p>Named after the Memcached "touch" command; semantics are strictly narrowed to
 * EXPIREAT absolute timestamps (not Redis {@code TOUCH} "refresh LRU", nor relative TTL).
 *
 * <p>Backend mappings:
 * <ul>
 *   <li>Redis    : {@code PEXPIREAT key expireAtMs}</li>
 *   <li>DynamoDB : {@code updateItem} overwriting TTL field with {@code expireAtMs/1000} (seconds)</li>
 *   <li>HBase    : column-family TTL does not support per-row; engine should not implement this interface</li>
 * </ul>
 *
 * <p><b>Idempotency guarantee</b>: the input is an absolute timestamp. Orchestrator retry loops
 * and compensation replays share the same {@code expireAtMs}, ensuring strict idempotency.
 * Implementations must not call {@code System.currentTimeMillis()} internally.
 *
 * @param <T> wrapper type, typically a {@code KvValueWrapper} subclass (only the key field is used)
 * @author Trip.com Group
 */
public interface TouchCapable<T> {

    /**
     * Set the expiration time of keys in wrappers to expireAtMs (Unix millisecond timestamp).
     *
     * @param expireAtMs absolute expiration time, Unix ms; &lt;= current time means immediate expiry
     */
    OperationResult<?> touch(List<T> wrappers, long expireAtMs, String commandKey);
}
