package com.ctrip.garfield.engine.wrapper;

import com.ctrip.garfield.common.lock.LockEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Base wrapper for KV engine operations. Holds the storage-level key, CAS
 * version, optional lock entity, and per-item failure slot. Subclasses add
 * engine-specific payload (e.g. serialized value bytes).
 *
 * <p>The engine sets {@code failureType} on individual items that fail in
 * a batch (partial failure tracking).
 *
 * @author Trip.com Group
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KvValueWrapper {

    /** Storage-level primary key. */
    private String key;

    /** CAS version for optimistic locking. Null means no version check. */
    private Long oldVersion;
    private LockEntity lockEntity;
    /** Set by the engine on per-item failure (e.g. "CAS_CONFLICT", "LOCK_FAILED"). */
    private String failureType;
}
