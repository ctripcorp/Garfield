package com.ctrip.garfield.process.route;

import com.ctrip.garfield.common.config.StorageEngineConfig;
import com.ctrip.garfield.common.enums.GarfieldStorageType;
import com.ctrip.garfield.common.enums.ProcessType;
import com.ctrip.garfield.common.enums.StorageType;
import com.ctrip.garfield.common.spi.StorageEngine;
import com.ctrip.garfield.common.spi.StorageEngineFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StorageEngineRegistryTest {

    StorageEngineRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new StorageEngineRegistry();
    }

    @Test
    void register_andCreateEngine_succeeds() {
        registry.register(testFactory(GarfieldStorageType.REDIS, ProcessType.KV));

        StorageEngineConfig config = new StorageEngineConfig();
        config.setStorageType(GarfieldStorageType.REDIS);
        StorageEngine engine = registry.createEngine(config);
        assertEquals("REDIS", engine.getStorageType());
    }

    @Test
    void createEngine_noFactory_throwsException() {
        StorageEngineConfig config = new StorageEngineConfig();
        config.setStorageType(GarfieldStorageType.MYSQL);
        assertThrows(IllegalStateException.class, () -> registry.createEngine(config));
    }

    @Test
    void validateCombination_unsupported_throwsException() {
        registry.register(testFactory(GarfieldStorageType.REDIS, ProcessType.KV));
        assertThrows(IllegalStateException.class, () ->
                registry.validateCombination(GarfieldStorageType.REDIS, ProcessType.MESSAGE));
    }

    @Test
    void validateCombination_supported_noException() {
        registry.register(testFactory(GarfieldStorageType.REDIS, ProcessType.KV));
        assertDoesNotThrow(() -> registry.validateCombination(GarfieldStorageType.REDIS, ProcessType.KV));
    }

    private StorageEngineFactory testFactory(StorageType type, ProcessType... capabilities) {
        Set<ProcessType> supported = EnumSet.noneOf(ProcessType.class);
        for (ProcessType cap : capabilities) {
            supported.add(cap);
        }
        return new StorageEngineFactory() {
            @Override public StorageType storageType() { return type; }
            @Override public Set<ProcessType> supportedProcessTypes() { return supported; }
            @Override public StorageEngine createEngine(StorageEngineConfig config) { return () -> type.name(); }
        };
    }
}
