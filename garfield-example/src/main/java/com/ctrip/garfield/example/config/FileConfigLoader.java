package com.ctrip.garfield.example.config;

import com.ctrip.garfield.common.config.ConfigLoader;
import com.ctrip.garfield.common.config.GarfieldObjectMappers;
import com.ctrip.garfield.common.config.StorageConfig;
import com.ctrip.garfield.common.config.StorageTypeRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.function.Consumer;

/**
 * Reference implementation: JSON file backed by JDK {@link java.nio.file.WatchService}
 * for hot-reload. Use this as a structural reference when implementing
 * {@link ConfigLoader} against your own config source.
 *
 * @author Trip.com Group
 */
@Slf4j
public class FileConfigLoader implements ConfigLoader {

    private static final long CONFIG_RELOAD_DEBOUNCE_MS = 100;

    private final String configPath;
    private final ObjectMapper objectMapper;

    public FileConfigLoader(String configPath, StorageTypeRegistry registry) {
        this.configPath = configPath;
        this.objectMapper = GarfieldObjectMappers.create(registry);
    }

    @Override
    public StorageConfig load() {
        try {
            return objectMapper.readValue(new File(configPath), StorageConfig.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load config from " + configPath, e);
        }
    }

    @Override
    public void watch(Consumer<StorageConfig> callback) {
        Path path = Path.of(configPath);
        Path dir = path.getParent();
        if (dir == null) {
            log.warn("Cannot watch config file without parent directory: {}", configPath);
            return;
        }
        Path fileName = path.getFileName();

        Thread watchThread = new Thread(() -> {
            try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
                dir.register(watcher,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_CREATE);
                while (!Thread.currentThread().isInterrupted()) {
                    WatchKey key = watcher.take();
                    boolean needReload = false;
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (fileName.equals(event.context())) {
                            needReload = true;
                        }
                    }
                    if (needReload) {
                        try {
                            Thread.sleep(CONFIG_RELOAD_DEBOUNCE_MS);
                            callback.accept(load());
                        } catch (Exception e) {
                            log.warn("Config reload failed for {}", configPath, e);
                        }
                    }
                    key.reset();
                }
            } catch (IOException e) {
                log.error("Watch service failed for {}", configPath, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "garfield-config-watcher");
        watchThread.setDaemon(true);
        watchThread.start();
    }
}
