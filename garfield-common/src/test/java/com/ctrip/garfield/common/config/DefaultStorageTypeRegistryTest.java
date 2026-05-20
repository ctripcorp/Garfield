package com.ctrip.garfield.common.config;

import com.ctrip.garfield.common.enums.GarfieldStorageType;
import com.ctrip.garfield.common.enums.ProcessType;
import com.ctrip.garfield.common.enums.StorageType;
import com.ctrip.garfield.common.exception.DuplicateStorageTypeException;
import com.ctrip.garfield.common.exception.NoSuchStorageTypeException;
import com.ctrip.garfield.common.spi.StorageEngine;
import com.ctrip.garfield.common.spi.StorageEngineFactory;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DefaultStorageTypeRegistryTest {

    enum AltType implements StorageType { REDIS }   // same name as GarfieldStorageType.REDIS but a different instance

    @Test
    void constructor_aggregatesFactories() {
        DefaultStorageTypeRegistry r = new DefaultStorageTypeRegistry(
                List.of(factory(GarfieldStorageType.REDIS), factory(GarfieldStorageType.KAFKA)));
        assertEquals(Set.of(GarfieldStorageType.REDIS, GarfieldStorageType.KAFKA), r.all());
    }

    @Test
    void resolve_exactMatch() {
        DefaultStorageTypeRegistry r = new DefaultStorageTypeRegistry(
                List.of(factory(GarfieldStorageType.REDIS)));
        assertSame(GarfieldStorageType.REDIS, r.resolve("REDIS"));
    }

    @Test
    void resolve_caseInsensitive() {
        DefaultStorageTypeRegistry r = new DefaultStorageTypeRegistry(
                List.of(factory(GarfieldStorageType.REDIS)));
        assertSame(GarfieldStorageType.REDIS, r.resolve("redis"));
        assertSame(GarfieldStorageType.REDIS, r.resolve("Redis"));
    }

    @Test
    void resolve_trimsWhitespace() {
        DefaultStorageTypeRegistry r = new DefaultStorageTypeRegistry(
                List.of(factory(GarfieldStorageType.REDIS)));
        assertSame(GarfieldStorageType.REDIS, r.resolve("  redis  "));
    }

    @Test
    void resolve_unknown_throws() {
        DefaultStorageTypeRegistry r = new DefaultStorageTypeRegistry(
                List.of(factory(GarfieldStorageType.REDIS)));
        NoSuchStorageTypeException ex = assertThrows(NoSuchStorageTypeException.class,
                () -> r.resolve("MONGODB"));
        assertTrue(ex.getMessage().contains("MONGODB"));
        assertTrue(ex.getMessage().contains("REDIS"));  // available list
    }

    @Test
    void resolve_blank_throws() {
        DefaultStorageTypeRegistry r = new DefaultStorageTypeRegistry(
                List.of(factory(GarfieldStorageType.REDIS)));
        assertThrows(NoSuchStorageTypeException.class, () -> r.resolve(""));
        assertThrows(NoSuchStorageTypeException.class, () -> r.resolve("   "));
    }

    @Test
    void resolve_null_throws() {
        DefaultStorageTypeRegistry r = new DefaultStorageTypeRegistry(Collections.emptyList());
        assertThrows(NoSuchStorageTypeException.class, () -> r.resolve(null));
    }

    @Test
    void register_sameInstance_idempotent() {
        DefaultStorageTypeRegistry r = new DefaultStorageTypeRegistry(
                List.of(factory(GarfieldStorageType.REDIS)));
        assertDoesNotThrow(() -> r.register(GarfieldStorageType.REDIS));
        assertEquals(1, r.all().size());
    }

    @Test
    void register_differentInstanceSameKey_throws() {
        DefaultStorageTypeRegistry r = new DefaultStorageTypeRegistry(
                List.of(factory(GarfieldStorageType.REDIS)));
        DuplicateStorageTypeException ex = assertThrows(DuplicateStorageTypeException.class,
                () -> r.register(AltType.REDIS));
        assertTrue(ex.getMessage().contains("REDIS"));
    }

    @Test
    void constructor_nullStorageTypeFromFactory_throws() {
        StorageEngineFactory bad = new StorageEngineFactory() {
            @Override public StorageType storageType() { return null; }
            @Override public Set<ProcessType> supportedProcessTypes() { return Set.of(); }
            @Override public StorageEngine createEngine(StorageEngineConfig c) { return null; }
        };
        Exception ex = assertThrows(Exception.class, () -> new DefaultStorageTypeRegistry(List.of(bad)));
        assertTrue(ex.getMessage().contains("null storageType"));
    }

    @Test
    void register_null_throws() {
        DefaultStorageTypeRegistry r = new DefaultStorageTypeRegistry(Collections.emptyList());
        assertThrows(IllegalArgumentException.class, () -> r.register(null));
    }

    @Test
    void emptyFactories_allowed() {
        DefaultStorageTypeRegistry r = new DefaultStorageTypeRegistry(Collections.emptyList());
        assertTrue(r.all().isEmpty());
    }

    private StorageEngineFactory factory(StorageType type) {
        return new StorageEngineFactory() {
            @Override public StorageType storageType() { return type; }
            @Override public Set<ProcessType> supportedProcessTypes() { return Set.of(); }
            @Override public StorageEngine createEngine(StorageEngineConfig c) { return null; }
        };
    }
}
