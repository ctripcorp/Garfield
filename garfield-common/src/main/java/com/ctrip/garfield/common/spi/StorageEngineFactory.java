package com.ctrip.garfield.common.spi;

import com.ctrip.garfield.common.config.StorageEngineConfig;
import com.ctrip.garfield.common.enums.ProcessType;
import com.ctrip.garfield.common.enums.StorageType;

import java.util.Set;

/**
 * SPI for creating storage engine instances from configuration.
 *
 * <p>Users implement this interface to register their storage technology (e.g.
 * Jedis for Redis, KafkaProducer for Kafka). The factory declares which
 * {@link ProcessType}s it supports, allowing the framework to validate
 * ProcessType x StorageType combinations at route assembly time.
 *
 * @author Trip.com Group
 */
public interface StorageEngineFactory {
    StorageType storageType();
    Set<ProcessType> supportedProcessTypes();
    StorageEngine createEngine(StorageEngineConfig config);
}
