package com.ctrip.garfield.common.config;

import java.util.function.Consumer;

/**
 * SPI for loading and watching Garfield configuration.
 *
 * <p>The example module provides a reference implementation based on a JSON file.
 * Users are expected to supply their own implementation backed by any config
 * source (file, Apollo, or any config center).
 *
 * @author Trip.com Group
 */
public interface ConfigLoader {
    StorageConfig load();
    void watch(Consumer<StorageConfig> callback);
}
