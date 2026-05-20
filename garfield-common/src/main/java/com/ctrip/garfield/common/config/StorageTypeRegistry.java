package com.ctrip.garfield.common.config;

import com.ctrip.garfield.common.enums.StorageType;

import java.util.Set;

/**
 * Central registry of all {@link StorageType} instances known to the framework.
 *
 * <p>Data source: aggregated from {@code StorageEngineFactory.storageType()} at startup.
 * Callers normally do not need to invoke {@link #register} directly — it is kept as
 * an escape hatch for tests and manual assembly.
 *
 * <p>Normalization rule: {@code rawName.trim().toUpperCase(Locale.ROOT)}.
 *
 * @author Trip.com Group
 */
public interface StorageTypeRegistry {

    /**
     * Entry point for JSON deserialization. Normalizes {@code rawName} then looks it up;
     * throws immediately if not found.
     *
     * @throws com.ctrip.garfield.common.exception.NoSuchStorageTypeException
     */
    StorageType resolve(String rawName);

    /**
     * Programmatically appends a type. Same key + same instance: idempotent.
     * Same key + different instance: throws.
     *
     * @throws com.ctrip.garfield.common.exception.DuplicateStorageTypeException
     */
    void register(StorageType type);

    Set<StorageType> all();
}
