package com.ctrip.garfield.common.exception;

import com.ctrip.garfield.common.enums.ErrorCode;

import java.util.Set;

/**
 * Thrown when a raw name has no matching entry in the registry during
 * deserialization or {@code resolve}. The exception message includes all
 * registered keys to help diagnose typos or missing registrations.
 *
 * @author Trip.com Group
 */
public class NoSuchStorageTypeException extends GarfieldException {
    public NoSuchStorageTypeException(String rawName, Set<String> availableKeys) {
        super(ErrorCode.UNKNOWN_ERROR, "Unknown storageType: '" + rawName + "'; available=" + availableKeys);
    }
}
