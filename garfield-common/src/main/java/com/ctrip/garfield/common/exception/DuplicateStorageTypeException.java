package com.ctrip.garfield.common.exception;

import com.ctrip.garfield.common.enums.ErrorCode;
import com.ctrip.garfield.common.enums.StorageType;

/**
 * Thrown when the same normalized key is claimed by two different {@link StorageType} instances.
 * This typically happens when two enums share the same name (e.g., both named {@code REDIS}
 * in different packages) and is a startup-time configuration error.
 *
 * @author Trip.com Group
 */
public class DuplicateStorageTypeException extends GarfieldException {
    public DuplicateStorageTypeException(String key, StorageType existing, StorageType incoming) {
        super(ErrorCode.UNKNOWN_ERROR, "Duplicate storageType key '" + key + "': existing="
                + existing.getClass().getName() + "." + existing.name()
                + ", incoming=" + incoming.getClass().getName() + "." + incoming.name());
    }
}
