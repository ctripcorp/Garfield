package com.ctrip.garfield.example.custom;

import com.ctrip.garfield.common.enums.StorageType;

/**
 * Example custom {@link StorageType} enum showing how a third party can register
 * its own storage medium without modifying any framework code.
 *
 * <p>In a real project, replace {@code MY_CUSTOM_STORAGE} with a value that
 * identifies your own storage medium. The framework only relies on {@code name()};
 * no framework source needs to be touched.
 *
 * @author Trip.com Group
 */
public enum MyStorageType implements StorageType {
    MY_CUSTOM_STORAGE
}
