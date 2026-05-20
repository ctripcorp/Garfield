package com.ctrip.garfield.common.config;

import com.ctrip.garfield.common.enums.StorageType;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;

import java.io.IOException;

/**
 * Jackson deserializer: JSON string → {@link StorageType} instance.
 * Normalization and lookup are delegated to {@link StorageTypeRegistry#resolve(String)}.
 *
 * <p>Two Jackson paths are overridden to enforce the "JSON null → exception" contract:
 * <ul>
 *   <li>{@link #deserialize}: handles ordinary string tokens and other non-null nodes;</li>
 *   <li>{@link #getNullValue}: handles the JSON {@code null} token — Jackson does not call
 *       {@code deserialize} for null tokens and would return Java {@code null} by default,
 *       so this override is required to throw consistently.</li>
 * </ul>
 *
 * @author Trip.com Group
 */
public class StorageTypeDeserializer extends JsonDeserializer<StorageType> {

    private final StorageTypeRegistry registry;

    public StorageTypeDeserializer(StorageTypeRegistry registry) {
        this.registry = registry;
    }

    @Override
    public StorageType deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String raw = p.getValueAsString();
        if (raw == null) {
            throw JsonMappingException.from(p, "storageType must not be null");
        }
        return registry.resolve(raw);
    }

    @Override
    // Jackson routes a JSON null token to getNullValue(), which returns Java null by default.
    // Override to match the null guard in deserialize() and keep the contract consistent.
    public StorageType getNullValue(DeserializationContext ctxt) throws JsonMappingException {
        throw JsonMappingException.from(ctxt, "storageType must not be null");
    }
}
