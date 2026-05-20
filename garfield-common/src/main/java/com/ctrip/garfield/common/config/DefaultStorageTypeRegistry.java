package com.ctrip.garfield.common.config;

import com.ctrip.garfield.common.enums.StorageType;
import com.ctrip.garfield.common.exception.DuplicateStorageTypeException;
import com.ctrip.garfield.common.exception.NoSuchStorageTypeException;
import com.ctrip.garfield.common.spi.StorageEngineFactory;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe default implementation of {@link StorageTypeRegistry}.
 *
 * <p>At construction time, aggregates all types from {@code StorageEngineFactory.storageType()}
 * as the authoritative data source. Additional types may be appended programmatically via
 * {@link #register} (useful for tests and manual assembly).
 *
 * <p>Normalized key: {@code rawName.trim().toUpperCase(Locale.ROOT)}.
 *
 * @author Trip.com Group
 */
@Slf4j
public class DefaultStorageTypeRegistry implements StorageTypeRegistry {

    private final Map<String, StorageType> map = new ConcurrentHashMap<>();

    public DefaultStorageTypeRegistry(Collection<StorageEngineFactory> factories) {
        for (StorageEngineFactory f : factories) {
            StorageType t = f.storageType();
            if (t == null) {
                throw new IllegalStateException(
                        "Factory returned null storageType: " + f.getClass().getName());
            }
            register(t);
        }
        log.info("StorageTypeRegistry initialized with {} types: {}", map.size(), map.keySet());
    }

    @Override
    public void register(StorageType type) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        String key = normalize(type.name());
        map.merge(key, type, (existing, incoming) -> {
            if (existing == incoming) {
                return existing;
            }
            throw new DuplicateStorageTypeException(key, existing, incoming);
        });
    }

    @Override
    public StorageType resolve(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            throw new NoSuchStorageTypeException(rawName, new HashSet<>(map.keySet()));
        }
        StorageType t = map.get(normalize(rawName));
        if (t == null) {
            throw new NoSuchStorageTypeException(rawName, new HashSet<>(map.keySet()));
        }
        return t;
    }

    @Override
    public Set<StorageType> all() {
        return Collections.unmodifiableSet(new HashSet<>(map.values()));
    }

    private static String normalize(String raw) {
        return raw.trim().toUpperCase(Locale.ROOT);
    }
}
