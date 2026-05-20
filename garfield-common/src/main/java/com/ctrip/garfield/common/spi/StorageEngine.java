package com.ctrip.garfield.common.spi;

/**
 * Top-level marker for all storage engines. Each engine declares its storage
 * type (e.g. "REDIS", "KAFKA") and implements one or more capability interfaces
 * ({@code KvCapable}, {@code HashCapable}, {@code MessageCapable}, {@code ServiceCallCapable}).
 *
 * <p>Engines are created by {@link StorageEngineFactory} and registered in
 * {@code StorageEngineRegistry} during route initialization.
 *
 * @author Trip.com Group
 */
public interface StorageEngine {
    String getStorageType();
}
