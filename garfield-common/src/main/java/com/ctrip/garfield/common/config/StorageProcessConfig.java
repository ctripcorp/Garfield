package com.ctrip.garfield.common.config;

import com.ctrip.garfield.common.enums.ProcessType;
import lombok.Data;

/**
 * Links a storage engine to a data access pattern and transfer bean.
 * One {@link ProcessConfig} references multiple instances of this class
 * for its leader, follower, and backup processes.
 *
 * @author Trip.com Group
 */
@Data
public class StorageProcessConfig {
    /** References {@link StorageEngineConfig#getStorageId()}. */
    private String engineId;
    private ProcessType processType;
    /** Spring bean name of the Transfer implementation. */
    private String transferName;
}
