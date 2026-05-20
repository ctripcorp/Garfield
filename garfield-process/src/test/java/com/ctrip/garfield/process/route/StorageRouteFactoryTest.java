package com.ctrip.garfield.process.route;

import com.ctrip.garfield.common.config.ConfigLoader;
import com.ctrip.garfield.common.config.ProcessConfig;
import com.ctrip.garfield.common.config.StorageEngineConfig;
import com.ctrip.garfield.common.config.StorageProcessConfig;
import com.ctrip.garfield.common.config.StorageConfig;
import com.ctrip.garfield.common.enums.GarfieldStorageType;
import com.ctrip.garfield.common.enums.ProcessType;
import com.ctrip.garfield.common.enums.StorageType;
import com.ctrip.garfield.common.exception.NoStorageRouteException;
import com.ctrip.garfield.common.model.OperationResult;
import com.ctrip.garfield.common.spi.RateLimiter;
import com.ctrip.garfield.common.spi.defaults.NoOpRateLimiter;
import com.ctrip.garfield.common.spi.StorageEngine;
import com.ctrip.garfield.common.spi.StorageEngineFactory;
import com.ctrip.garfield.engine.capability.KvCapable;
import com.ctrip.garfield.engine.wrapper.KvValueWrapper;
import com.ctrip.garfield.transfer.KvTransfer;
import com.ctrip.garfield.transfer.ReadIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class StorageRouteFactoryTest {

    StorageEngineRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new StorageEngineRegistry();
        registry.register(new StorageEngineFactory() {
            @Override public StorageType storageType() { return GarfieldStorageType.REDIS; }
            @Override public Set<ProcessType> supportedProcessTypes() { return EnumSet.of(ProcessType.KV); }
            @Override public StorageEngine createEngine(StorageEngineConfig config) { return new TestKvEngine(); }
        });
    }

    @Test
    void init_buildsRoutesFromConfig() {
        StorageConfig storageConfig = buildTestConfig();
        ConfigLoader configLoader = new ConfigLoader() {
            @Override public StorageConfig load() { return storageConfig; }
            @Override public void watch(Consumer<StorageConfig> callback) {}
        };

        StorageRouteFactory factory = new StorageRouteFactory(registry, configLoader, name -> new DummyKvTransfer(), new NoOpRateLimiter());
        factory.init();

        StorageRoute route = factory.getRoute("TestData");
        assertNotNull(route);
        assertNotNull(route.getLeader());
    }

    @Test
    void getRoute_unknownReqClass_throwsException() {
        StorageConfig storageConfig = buildTestConfig();
        ConfigLoader configLoader = new ConfigLoader() {
            @Override public StorageConfig load() { return storageConfig; }
            @Override public void watch(Consumer<StorageConfig> callback) {}
        };
        StorageRouteFactory factory = new StorageRouteFactory(registry, configLoader, name -> new DummyKvTransfer(), new NoOpRateLimiter());
        factory.init();

        assertThrows(NoStorageRouteException.class, () -> factory.getRoute("UnknownData"));
    }

    @Test
    void onConfigChange_emptyList_preservesExistingRoutes() {
        StorageConfig initialConfig = buildTestConfig();
        ConfigLoader configLoader = new ConfigLoader() {
            @Override public StorageConfig load() { return initialConfig; }
            @Override public void watch(Consumer<StorageConfig> callback) {}
        };
        StorageRouteFactory factory = new StorageRouteFactory(registry, configLoader, name -> new DummyKvTransfer(), new NoOpRateLimiter());
        factory.init();

        StorageConfig updateConfig = new StorageConfig();
        updateConfig.setStorageEngineConfigs(Collections.emptyList());
        updateConfig.setProcessConfigs(Collections.emptyList());
        factory.onConfigChange(updateConfig);

        assertNotNull(factory.getRoute("TestData"));
    }

    private StorageConfig buildTestConfig() {
        StorageEngineConfig engineConfig = new StorageEngineConfig();
        engineConfig.setStorageId("test_redis");
        engineConfig.setStorageType(GarfieldStorageType.REDIS);
        engineConfig.setEnabled(true);
        engineConfig.setVersion(1);
        Map<String, Object> props = new HashMap<>();
        props.put("host", "localhost");
        props.put("port", 6379);
        engineConfig.setProperties(props);

        StorageProcessConfig leaderProcessConfig = new StorageProcessConfig();
        leaderProcessConfig.setEngineId("test_redis");
        leaderProcessConfig.setProcessType(ProcessType.KV);
        leaderProcessConfig.setTransferName("testKvTransfer");

        ProcessConfig processConfig = new ProcessConfig();
        processConfig.setReqClassName("TestData");
        processConfig.setVersion(1);
        processConfig.setLeaderProcess(leaderProcessConfig);

        StorageConfig config = new StorageConfig();
        config.setStorageEngineConfigs(List.of(engineConfig));
        config.setProcessConfigs(List.of(processConfig));
        return config;
    }

    static class TestKvEngine implements StorageEngine, KvCapable<KvValueWrapper> {
        @Override public String getStorageType() { return "REDIS"; }
        @Override public OperationResult<KvValueWrapper> batchGet(List<KvValueWrapper> w, String k) { return new OperationResult<>(); }
        @Override public OperationResult<?> batchPut(List<KvValueWrapper> w, String k) { return new OperationResult<>(); }
        @Override public OperationResult<?> batchDelete(List<KvValueWrapper> w, String k) { return new OperationResult<>(); }
    }

    static class DummyKvTransfer implements KvTransfer<Object, Object, KvValueWrapper> {
        @Override public KvValueWrapper toStorage(Object data) { return new KvValueWrapper(); }
        @Override public List<Object> storageToObject(KvValueWrapper wrapper) { return Collections.emptyList(); }
        @Override public ReadIntent<KvValueWrapper> buildReadIntent(com.ctrip.garfield.common.context.GarfieldContext<Object, ?> ctx) {
            return new ReadIntent.KeyLookup<>(ctx.getDataInfos().stream().map(d -> new KvValueWrapper()).toList());
        }
    }
}
