package com.ctrip.garfield.common.config;

import com.ctrip.garfield.common.enums.StorageType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * Factory helper that creates an {@link ObjectMapper} pre-configured with
 * a {@link StorageTypeDeserializer}.
 *
 * <p>Centralizes Jackson configuration to avoid duplicate module registrations;
 * all config-loading code should obtain its mapper through this entry point.
 *
 * @author Trip.com Group
 */
public final class GarfieldObjectMappers {

    private GarfieldObjectMappers() {
    }

    public static ObjectMapper create() {
        return new ObjectMapper();
    }

    public static ObjectMapper create(StorageTypeRegistry registry) {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(StorageType.class, new StorageTypeDeserializer(registry));
        return new ObjectMapper().registerModule(module);
    }
}
