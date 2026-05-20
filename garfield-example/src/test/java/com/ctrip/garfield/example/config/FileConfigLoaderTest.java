package com.ctrip.garfield.example.config;

import com.ctrip.garfield.common.config.DefaultStorageTypeRegistry;
import com.ctrip.garfield.common.config.StorageConfig;
import com.ctrip.garfield.common.config.StorageTypeRegistry;
import com.ctrip.garfield.common.enums.GarfieldStorageType;
import com.ctrip.garfield.common.enums.ProcessType;
import com.ctrip.garfield.common.enums.StorageType;
import com.ctrip.garfield.common.spi.StorageEngine;
import com.ctrip.garfield.common.spi.StorageEngineFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FileConfigLoaderTest {

    @TempDir
    Path tempDir;

    private StorageTypeRegistry registry;

    @BeforeEach
    void setup() {
        registry = new DefaultStorageTypeRegistry(List.of(
                stubFactory(GarfieldStorageType.REDIS),
                stubFactory(GarfieldStorageType.KAFKA)));
    }

    @Test
    void load_validJson_parsesCorrectly() throws IOException {
        String json = """
                {
                  "storageEngineConfigs": [
                    {
                      "storageId": "test_redis",
                      "storageType": "REDIS",
                      "enabled": true,
                      "version": 1,
                      "properties": {"host": "localhost", "port": 6379}
                    }
                  ],
                  "processConfigs": [
                    {
                      "reqClassName": "TestData",
                      "version": 1,
                      "leaderProcess": {
                        "engineId": "test_redis",
                        "processType": "KV",
                        "transferName": "testTransfer"
                      }
                    }
                  ]
                }
                """;
        Path configFile = tempDir.resolve("garfield-config.json");
        Files.writeString(configFile, json);

        FileConfigLoader loader = new FileConfigLoader(configFile.toString(), registry);
        StorageConfig config = loader.load();

        assertNotNull(config);
        assertEquals(1, config.getStorageEngineConfigs().size());
        assertSame(GarfieldStorageType.REDIS, config.getStorageEngineConfigs().get(0).getStorageType());
    }

    @Test
    void load_lowerCaseStorageType_normalized() throws IOException {
        String json = """
                {
                  "storageEngineConfigs": [
                    {"storageId": "k", "storageType": "kafka", "enabled": true, "version": 1, "properties": {}}
                  ],
                  "processConfigs": []
                }
                """;
        Path configFile = tempDir.resolve("garfield-config.json");
        Files.writeString(configFile, json);

        StorageConfig config = new FileConfigLoader(configFile.toString(), registry).load();
        assertSame(GarfieldStorageType.KAFKA, config.getStorageEngineConfigs().get(0).getStorageType());
    }

    @Test
    void load_invalidPath_throwsException() {
        FileConfigLoader loader = new FileConfigLoader("/nonexistent/path.json", registry);
        assertThrows(UncheckedIOException.class, loader::load);
    }

    @Test
    void load_invalidJson_throwsException() throws IOException {
        Path configFile = tempDir.resolve("bad.json");
        Files.writeString(configFile, "not json");

        FileConfigLoader loader = new FileConfigLoader(configFile.toString(), registry);
        assertThrows(UncheckedIOException.class, loader::load);
    }

    private StorageEngineFactory stubFactory(StorageType type) {
        return new StorageEngineFactory() {
            @Override public StorageType storageType() { return type; }
            @Override public Set<ProcessType> supportedProcessTypes() { return Set.of(ProcessType.KV); }
            @Override public StorageEngine createEngine(com.ctrip.garfield.common.config.StorageEngineConfig c) { return null; }
        };
    }
}
