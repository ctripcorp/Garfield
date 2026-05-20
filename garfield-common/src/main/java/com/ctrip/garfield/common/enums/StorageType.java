package com.ctrip.garfield.common.enums;

/**
 * Storage medium type — one orthogonal routing dimension in garfield.
 *
 * <p>The framework ships {@link GarfieldStorageType} as a built-in implementation;
 * users may extend it by implementing this interface with their own enum.
 * Implementors should be enums whose {@code name()} is stable and unique for the
 * lifetime of the process.
 *
 * <p>The framework does not rely on {@code equals/hashCode} internally —
 * {@code StorageTypeRegistry} uses the normalized string
 * ({@code name()} after trim + upper-case) as the lookup key.
 *
 * @author Trip.com Group
 */
public interface StorageType {
    String name();
}
