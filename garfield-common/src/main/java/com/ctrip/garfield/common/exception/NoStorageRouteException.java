package com.ctrip.garfield.common.exception;

import com.ctrip.garfield.common.enums.ErrorCode;
import lombok.Getter;

/**
 * Thrown when no routing rule matches the given request class name.
 *
 * @author Trip.com Group
 */
@Getter
public class NoStorageRouteException extends GarfieldException {

    private final String reqClassName;

    public NoStorageRouteException(String reqClassName) {
        super(ErrorCode.ROUTE_NOT_FOUND);
        this.reqClassName = reqClassName;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + ": " + reqClassName;
    }
}
