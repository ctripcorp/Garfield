package com.ctrip.garfield.common.config;

import com.ctrip.garfield.common.enums.GarfieldStorageType;
import com.ctrip.garfield.common.enums.StorageType;
import com.ctrip.garfield.common.exception.NoSuchStorageTypeException;
import com.ctrip.garfield.common.spi.StorageEngine;
import com.ctrip.garfield.common.spi.StorageEngineFactory;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StorageTypeDeserializerTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setup() {
        StorageTypeRegistry registry = new DefaultStorageTypeRegistry(List.of(new StorageEngineFactory() {
            @Override public StorageType storageType() { return GarfieldStorageType.REDIS; }
            @Override public Set<com.ctrip.garfield.common.enums.ProcessType> supportedProcessTypes() { return Set.of(); }
            @Override public StorageEngine createEngine(StorageEngineConfig c) { return null; }
        }));
        SimpleModule m = new SimpleModule();
        m.addDeserializer(StorageType.class, new StorageTypeDeserializer(registry));
        mapper = new ObjectMapper().registerModule(m);
    }

    @Test
    void deserialize_upperCase() throws Exception {
        assertSame(GarfieldStorageType.REDIS, mapper.readValue("\"REDIS\"", StorageType.class));
    }

    @Test
    void deserialize_lowerCase() throws Exception {
        assertSame(GarfieldStorageType.REDIS, mapper.readValue("\"redis\"", StorageType.class));
    }

    @Test
    void deserialize_withWhitespace() throws Exception {
        assertSame(GarfieldStorageType.REDIS, mapper.readValue("\" Redis \"", StorageType.class));
    }

    @Test
    void deserialize_empty_throws() {
        assertThrows(NoSuchStorageTypeException.class,
                () -> mapper.readValue("\"\"", StorageType.class));
    }

    @Test
    void deserialize_null_throws() {
        assertThrows(JsonMappingException.class,
                () -> mapper.readValue("null", StorageType.class));
    }

    @Test
    void deserialize_unknown_throws() {
        assertThrows(NoSuchStorageTypeException.class,
                () -> mapper.readValue("\"MONGODB\"", StorageType.class));
    }
}
