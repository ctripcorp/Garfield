package com.ctrip.garfield.common.config;

import lombok.Data;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StorageEngineConfigTest {

    @Test
    void getPropertiesAsConvertsMapToTypedPojo() {
        StorageEngineConfig config = new StorageEngineConfig();
        config.setProperties(Map.of(
                "clusterName", "product-daas-cache",
                "timeoutMs", 1500
        ));

        RedisProperties props = config.getPropertiesAs(RedisProperties.class);

        assertEquals("product-daas-cache", props.getClusterName());
        assertEquals(1500, props.getTimeoutMs());
    }

    @Data
    static class RedisProperties {
        private String clusterName;
        private int timeoutMs;
    }
}
