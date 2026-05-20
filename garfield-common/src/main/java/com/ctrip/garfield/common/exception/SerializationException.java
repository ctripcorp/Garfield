package com.ctrip.garfield.common.exception;

import com.ctrip.garfield.common.enums.ErrorCode;

/**
 * Thrown on serialization or deserialization failure. {@code GarfieldSerializer}
 * implementations are expected to wrap all underlying exceptions in this type.
 *
 * @author Trip.com Group
 */
public class SerializationException extends GarfieldException {

    public SerializationException(Throwable cause) {
        super(ErrorCode.SERIALIZATION_FAILED, cause);
    }
}
