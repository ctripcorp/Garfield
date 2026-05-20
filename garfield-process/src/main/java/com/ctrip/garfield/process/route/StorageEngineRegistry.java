package com.ctrip.garfield.process.route;

import com.ctrip.garfield.common.config.StorageEngineConfig;
import com.ctrip.garfield.common.enums.ProcessType;
import com.ctrip.garfield.common.enums.StorageType;
import com.ctrip.garfield.common.spi.StorageEngine;
import com.ctrip.garfield.common.spi.StorageEngineFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry of {@link StorageEngineFactory} instances, keyed by {@link StorageType}.
 * Used by {@link StorageRouteFactory} to create engine instances and validate
 * that a given StorageType supports the requested ProcessType.
 *
 * @author Trip.com Group
 */
public class StorageEngineRegistry {

    private final Map<StorageType, StorageEngineFactory> registry = new HashMap<>();

    public void register(StorageEngineFactory factory) {
        registry.put(factory.storageType(), factory);
    }

    public StorageEngine createEngine(StorageEngineConfig config) {
        StorageEngineFactory factory = registry.get(config.getStorageType());
        if (factory == null) {
            throw new IllegalStateException("No factory registered for storage type: " + config.getStorageType());
        }
        return factory.createEngine(config);
    }

    public void validateCombination(StorageType storageType, ProcessType processType) {
        StorageEngineFactory factory = registry.get(storageType);
        if (factory == null) {
            throw new IllegalStateException("No factory registered for storage type: " + storageType);
        }
        if (!factory.supportedProcessTypes().contains(processType)) {
            throw new IllegalStateException(storageType + " does not support process type: " + processType);
        }
    }
}
