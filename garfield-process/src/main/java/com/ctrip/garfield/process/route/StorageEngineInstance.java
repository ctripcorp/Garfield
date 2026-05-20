package com.ctrip.garfield.process.route;

import com.ctrip.garfield.common.config.StorageEngineConfig;
import com.ctrip.garfield.common.spi.StorageEngine;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Pairs a runtime {@link StorageEngine} with its originating {@link StorageEngineConfig}.
 *
 * @author Trip.com Group
 */
@Data
@AllArgsConstructor
public class StorageEngineInstance {

    private final StorageEngineConfig storageEngineConfig;
    private final StorageEngine storageEngine;
}
