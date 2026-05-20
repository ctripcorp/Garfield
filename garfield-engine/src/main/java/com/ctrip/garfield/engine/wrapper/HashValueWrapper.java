package com.ctrip.garfield.engine.wrapper;

import com.ctrip.garfield.common.lock.LockEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Base wrapper for hash-based engine operations (Redis {@code HSET key field value}
 * semantics).
 *
 * <ul>
 *   <li>{@code key}   – outer key / hash name (first argument of Redis HSET)</li>
 *   <li>{@code field} – inner field within the hash (second argument of Redis HSET)</li>
 * </ul>
 *
 * <p>Subclasses add the serialized value. The engine sets {@code failureType}
 * on per-item failure, analogous to {@link KvValueWrapper}.
 *
 * @author Trip.com Group
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HashValueWrapper {

    /** Outer key (hash name). */
    private String key;
    /** Inner field within the hash. */
    private String field;
    private LockEntity lockEntity;
    /** Set by the engine on per-item failure. */
    private String failureType;
}
