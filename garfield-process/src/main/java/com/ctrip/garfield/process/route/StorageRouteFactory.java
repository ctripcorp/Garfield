package com.ctrip.garfield.process.route;

import com.ctrip.garfield.common.config.ConfigLoader;
import com.ctrip.garfield.common.config.ProcessConfig;
import com.ctrip.garfield.common.config.StorageEngineConfig;
import com.ctrip.garfield.common.config.StorageProcessConfig;
import com.ctrip.garfield.common.config.StorageConfig;
import com.ctrip.garfield.common.context.GarfieldContext;
import com.ctrip.garfield.common.exception.NoStorageRouteException;
import com.ctrip.garfield.common.spi.RateLimiter;
import com.ctrip.garfield.common.spi.StorageEngine;
import com.ctrip.garfield.engine.capability.HashCapable;
import com.ctrip.garfield.engine.capability.KvCapable;
import com.ctrip.garfield.engine.capability.MessageCapable;
import com.ctrip.garfield.engine.capability.ServiceCallCapable;
import com.ctrip.garfield.engine.capability.TouchCapable;
import com.ctrip.garfield.process.StorageProcess;
import com.ctrip.garfield.process.impl.HashStorageProcess;
import com.ctrip.garfield.process.impl.KvStorageProcess;
import com.ctrip.garfield.process.impl.MessageStorageProcess;
import com.ctrip.garfield.process.impl.ServiceStorageProcess;
import com.ctrip.garfield.process.impl.TouchStorageProcess;
import com.ctrip.garfield.transfer.HashKvTransfer;
import com.ctrip.garfield.transfer.KvTransfer;
import com.ctrip.garfield.transfer.MqTransfer;
import com.ctrip.garfield.transfer.ServiceTransfer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * Config-driven factory that builds and maintains {@link StorageRoute} instances.
 *
 * <p>On {@link #init()}: loads JSON config, creates engine instances via
 * {@link StorageEngineRegistry}, validates ProcessType x StorageType combinations,
 * assembles routes, and starts watching for config changes.
 *
 * <p>Hot-reload: on config file change, performs incremental updates by comparing
 * version numbers — only engines/routes with newer versions are rebuilt.
 *
 * @author Trip.com Group
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class StorageRouteFactory {

    private static final Logger log = LoggerFactory.getLogger(StorageRouteFactory.class);

    private final StorageEngineRegistry engineRegistry;
    private final ConfigLoader configLoader;
    private final Function<String, Object> beanResolver;
    private final RateLimiter rateLimiter;
    private final ReentrantLock configLock = new ReentrantLock();

    private volatile Map<String, StorageRoute> routeMap = Map.of();
    private volatile Map<String, StorageEngineInstance> engines = Map.of();

    public StorageRouteFactory(StorageEngineRegistry engineRegistry,
                               ConfigLoader configLoader,
                               Function<String, Object> beanResolver,
                               RateLimiter rateLimiter) {
        this.engineRegistry = engineRegistry;
        this.configLoader = configLoader;
        this.beanResolver = beanResolver;
        this.rateLimiter = rateLimiter;
    }

    public void init() {
        StorageConfig config = configLoader.load();
        configLock.lock();
        try {
            buildEngines(config.getStorageEngineConfigs());
            buildRoutes(config.getProcessConfigs());
        } finally {
            configLock.unlock();
        }
        configLoader.watch(this::onConfigChange);
        log.info("StorageRouteFactory initialized with {} routes", routeMap.size());
    }

    public void onConfigChange(StorageConfig newConfig) {
        configLock.lock();
        try {
            log.info("Config change detected, performing incremental update");
            buildEngines(newConfig.getStorageEngineConfigs());
            buildRoutes(newConfig.getProcessConfigs());
        } finally {
            configLock.unlock();
        }
    }

    public StorageRoute getRoute(String reqClassName) {
        Objects.requireNonNull(reqClassName, "reqClassName must not be null");
        StorageRoute route = routeMap.get(reqClassName);
        if (route == null) {
            throw new NoStorageRouteException(reqClassName);
        }
        return route;
    }

    public StorageRoute getRoute(GarfieldContext context) {
        return getRoute(context.getReqClassName());
    }

    private void buildEngines(List<StorageEngineConfig> configs) {
        if (configs == null) {
            return;
        }
        Map<String, StorageEngineInstance> current = this.engines;
        Map<String, StorageEngineInstance> newEngines = new HashMap<>(current);
        for (StorageEngineConfig config : configs) {
            if (!config.isEnabled()) {
                newEngines.remove(config.getStorageId());
                continue;
            }
            StorageEngineInstance existing = newEngines.get(config.getStorageId());
            if (existing != null && existing.getStorageEngineConfig().getVersion() >= config.getVersion()) {
                continue;
            }
            StorageEngine engine = engineRegistry.createEngine(config);
            newEngines.put(config.getStorageId(), new StorageEngineInstance(config, engine));
            log.info("Engine built: storageId={}, type={}", config.getStorageId(), config.getStorageType());
        }
        this.engines = Collections.unmodifiableMap(newEngines);
    }

    private void buildRoutes(List<ProcessConfig> configs) {
        if (configs == null) {
            return;
        }
        Map<String, StorageRoute> current = this.routeMap;
        Map<String, StorageRoute> newRouteMap = new HashMap<>(current);
        for (ProcessConfig pc : configs) {
            StorageRoute existingRoute = newRouteMap.get(pc.getReqClassName());
            if (existingRoute != null && existingRoute.getVersion() >= pc.getVersion()) {
                continue;
            }

            StorageRoute route = new StorageRoute();
            route.setVersion(pc.getVersion());

            if (pc.getLeaderProcess() != null) {
                StorageEngineInstance engineInstance = this.engines.get(pc.getLeaderProcess().getEngineId());
                if (engineInstance != null) {
                    engineRegistry.validateCombination(
                            engineInstance.getStorageEngineConfig().getStorageType(),
                            pc.getLeaderProcess().getProcessType());
                    route.setLeader(createProcess(pc.getLeaderProcess(), engineInstance));
                }
            }

            if (pc.getFollowerProcess() != null) {
                List<StorageProcess> followers = new ArrayList<>();
                for (StorageProcessConfig followerConfig : pc.getFollowerProcess()) {
                    StorageEngineInstance engineInstance = this.engines.get(followerConfig.getEngineId());
                    if (engineInstance != null) {
                        engineRegistry.validateCombination(
                                engineInstance.getStorageEngineConfig().getStorageType(),
                                followerConfig.getProcessType());
                        followers.add(createProcess(followerConfig, engineInstance));
                    }
                }
                route.setFollowers(followers);
            }

            route.initProcessMap();
            newRouteMap.put(pc.getReqClassName(), route);
            log.info("Route built: reqClassName={}", pc.getReqClassName());
        }
        this.routeMap = Collections.unmodifiableMap(newRouteMap);
    }

    private StorageProcess createProcess(StorageProcessConfig config, StorageEngineInstance engineInstance) {
        StorageEngine engine = engineInstance.getStorageEngine();
        Object transfer = beanResolver.apply(config.getTransferName());

        return switch (config.getProcessType()) {
            case KV -> {
                validateTransferType(transfer, KvTransfer.class, config);
                validateEngineType(engine, KvCapable.class, config);
                yield new KvStorageProcess(
                        (KvTransfer) transfer, (KvCapable) engine, engineInstance, config, rateLimiter);
            }
            case HASH -> {
                validateTransferType(transfer, HashKvTransfer.class, config);
                validateEngineType(engine, HashCapable.class, config);
                yield new HashStorageProcess(
                        (HashKvTransfer) transfer, (HashCapable) engine, engineInstance, config, rateLimiter);
            }
            case MESSAGE -> {
                validateTransferType(transfer, MqTransfer.class, config);
                validateEngineType(engine, MessageCapable.class, config);
                yield new MessageStorageProcess(
                        (MqTransfer) transfer, (MessageCapable) engine, engineInstance, config, rateLimiter);
            }
            case SERVICE_CALL -> {
                validateTransferType(transfer, ServiceTransfer.class, config);
                validateEngineType(engine, ServiceCallCapable.class, config);
                yield new ServiceStorageProcess(
                        (ServiceTransfer) transfer, (ServiceCallCapable) engine, engineInstance, config, rateLimiter);
            }
            case TOUCH -> {
                if (!(transfer instanceof KvTransfer) && !(transfer instanceof HashKvTransfer)) {
                    throw new IllegalStateException(
                            "TOUCH requires transfer to be KvTransfer or HashKvTransfer, got: "
                                    + transfer.getClass().getSimpleName()
                                    + " (transferName=" + config.getTransferName() + ")");
                }
                validateEngineType(engine, TouchCapable.class, config);
                yield new TouchStorageProcess(
                        (KvTransfer) transfer, (TouchCapable) engine, engineInstance, config, rateLimiter);
            }
            case SCAN, QUERY -> throw new IllegalStateException(
                    config.getProcessType() + " is no longer a valid processType for StorageProcessConfig. "
                            + "Migrate to KV or HASH — the Transfer's buildReadIntent() now decides the read path. "
                            + "(engineId=" + config.getEngineId() + ", transferName=" + config.getTransferName() + ")");
        };
    }

    private void validateTransferType(Object transfer, Class<?> expectedType, StorageProcessConfig config) {
        if (!expectedType.isInstance(transfer)) {
            throw new IllegalStateException(
                    config.getProcessType() + " requires transfer of type " + expectedType.getSimpleName()
                            + ", got: " + transfer.getClass().getSimpleName()
                            + " (transferName=" + config.getTransferName() + ")");
        }
    }

    private void validateEngineType(StorageEngine engine, Class<?> expectedCapability, StorageProcessConfig config) {
        if (!expectedCapability.isInstance(engine)) {
            throw new IllegalStateException(
                    config.getProcessType() + " requires engine to implement " + expectedCapability.getSimpleName()
                            + ", got: " + engine.getClass().getSimpleName()
                            + " (engineId=" + config.getEngineId() + ")");
        }
    }
}
