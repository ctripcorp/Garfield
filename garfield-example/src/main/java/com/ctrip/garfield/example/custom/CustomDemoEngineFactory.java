package com.ctrip.garfield.example.custom;

import com.ctrip.garfield.common.config.StorageEngineConfig;
import com.ctrip.garfield.common.enums.ProcessType;
import com.ctrip.garfield.common.enums.StorageType;
import com.ctrip.garfield.common.spi.CircuitBreaker;
import com.ctrip.garfield.common.spi.StorageEngine;
import com.ctrip.garfield.common.spi.StorageEngineFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * Example showing how to integrate a custom storage medium into garfield by registering
 * {@link MyStorageType#MY_CUSTOM_STORAGE} with the framework.
 *
 * <p>Three-step recipe:
 * <ol>
 *   <li>Define a custom enum implementing {@link StorageType} (see {@link MyStorageType}).</li>
 *   <li>Implement {@link StorageEngineFactory}; {@link #storageType()} must return the custom
 *       enum value. Spring will auto-discover this bean and register it with
 *       {@code StorageTypeRegistry} / {@code StorageEngineRegistry}.</li>
 *   <li>Add a {@code storageEngineConfigs} entry in {@code garfield-config.json} using the
 *       type name (case-insensitive) to enable routing to this medium.</li>
 * </ol>
 *
 * @author Trip.com Group
 */
@Component
@RequiredArgsConstructor
public class CustomDemoEngineFactory implements StorageEngineFactory {

    private final CircuitBreaker circuitBreaker;

    @Override
    public StorageType storageType() {
        return MyStorageType.MY_CUSTOM_STORAGE;
    }

    @Override
    public Set<ProcessType> supportedProcessTypes() {
        return EnumSet.of(ProcessType.KV);
    }

    @Override
    public StorageEngine createEngine(StorageEngineConfig config) {
        return new CustomDemoEngine(circuitBreaker);
    }
}
