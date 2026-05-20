package com.ctrip.garfield.example.engine;

import com.ctrip.garfield.common.config.StorageEngineConfig;
import com.ctrip.garfield.common.enums.GarfieldStorageType;
import com.ctrip.garfield.common.enums.ProcessType;
import com.ctrip.garfield.common.enums.StorageType;
import com.ctrip.garfield.common.spi.CircuitBreaker;
import com.ctrip.garfield.common.spi.StorageEngine;
import com.ctrip.garfield.common.spi.StorageEngineFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.EnumSet;
import java.util.Set;

/**
 * Factory that creates Jedis-based Redis KV engine instances.
 *
 * @author Trip.com Group
 */
@Component
@RequiredArgsConstructor
public class JedisEngineFactory implements StorageEngineFactory {

    private static final int DEFAULT_JEDIS_TIMEOUT_MS = 3000;

    private final CircuitBreaker circuitBreaker;

    @Override
    public StorageType storageType() {
        return GarfieldStorageType.REDIS;
    }

    @Override
    public Set<ProcessType> supportedProcessTypes() {
        return EnumSet.of(ProcessType.KV);
    }

    @Override
    public StorageEngine createEngine(StorageEngineConfig config) {
        Object hostObj = config.getProperties().get("host");
        if (hostObj == null) {
            throw new IllegalArgumentException("Missing required property 'host' for REDIS engine: storageId=" + config.getStorageId());
        }
        String host = (String) hostObj;

        Object portObj = config.getProperties().get("port");
        if (portObj == null) {
            throw new IllegalArgumentException("Missing required property 'port' for REDIS engine: storageId=" + config.getStorageId());
        }
        int port = ((Number) portObj).intValue();

        int timeout = config.getProperties().containsKey("timeout")
                ? ((Number) config.getProperties().get("timeout")).intValue()
                : DEFAULT_JEDIS_TIMEOUT_MS;
        JedisPool pool = new JedisPool(new JedisPoolConfig(), host, port, timeout);
        return new JedisKvEngine(pool, circuitBreaker);
    }
}
