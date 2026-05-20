package com.ctrip.garfield.common.config;

import lombok.Data;

import java.util.List;

/**
 * Root configuration model, deserialized from {@code garfield-config.json}.
 * Contains engine definitions and process routing rules.
 *
 * @author Trip.com Group
 */
@Data
public class StorageConfig {
    private List<StorageEngineConfig> storageEngineConfigs;
    private List<ProcessConfig> processConfigs;
}
