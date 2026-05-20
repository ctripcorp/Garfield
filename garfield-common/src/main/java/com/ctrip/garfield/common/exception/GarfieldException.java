package com.ctrip.garfield.common.exception;

import com.ctrip.garfield.common.enums.ErrorCode;
import lombok.Getter;

/**
 * Base runtime exception carrying an {@link ErrorCode} for all framework errors.
 *
 * @author Trip.com Group
 */
@Getter
public class GarfieldException extends RuntimeException {

    private final ErrorCode errorCode;

    public GarfieldException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public GarfieldException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public GarfieldException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
}
