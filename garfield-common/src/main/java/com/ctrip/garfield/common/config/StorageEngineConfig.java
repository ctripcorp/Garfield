package com.ctrip.garfield.common.config;

import com.ctrip.garfield.common.enums.StorageType;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.util.Map;

/**
 * Configuration for a single storage engine instance.
 * The {@code storageId} uniquely identifies this engine across all routes.
 *
 * @author Trip.com Group
 */
@Data
public class StorageEngineConfig {
    private static final ObjectMapper OBJECT_MAPPER = GarfieldObjectMappers.create();

    private StorageType storageType;
    /** Unique identifier referenced by {@link StorageProcessConfig#getEngineId()}. */
    private String storageId;
    @JsonProperty("enabled")
    private boolean enabled;
    /** Used for incremental hot-reload: only configs with a newer version are applied. */
    private long version;
    private boolean rateLimit;
    /** Storage-specific properties (e.g. host, port, topic). Passed to the engine factory. */
    private Map<String, Object> properties;

    public <T> T getPropertiesAs(Class<T> clazz) {
        return OBJECT_MAPPER.convertValue(properties, clazz);
    }
}
