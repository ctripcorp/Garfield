package com.ctrip.garfield.common.spi;

import java.util.concurrent.ExecutorService;

/**
 * Strategy for obtaining the executor used for asynchronous follower writes.
 *
 * @author Trip.com Group
 */
@FunctionalInterface
public interface FollowerExecutorProvider {

    ExecutorService getExecutor(String storageId);

    default void shutdown() {
    }
}
