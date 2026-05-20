package com.ctrip.garfield.common.config;

import lombok.Data;

import java.util.List;

/**
 * Routing rule that maps a {@code reqClassName} to its leader/follower storage processes.
 * This is the core config unit that drives multi-medium write orchestration.
 *
 * @author Trip.com Group
 */
@Data
public class ProcessConfig {
    /** The routing key — typically the business data class name (e.g. "OrderDataUnit"). */
    private String reqClassName;
    private StorageProcessConfig leaderProcess;
    private List<StorageProcessConfig> followerProcess;
    private long version;
}
