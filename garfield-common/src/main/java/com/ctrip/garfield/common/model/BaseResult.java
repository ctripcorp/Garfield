package com.ctrip.garfield.common.model;

import lombok.Data;

/**
 * Generic success/failure result returned to callers of the framework.
 *
 * @author Trip.com Group
 */
@Data
public class BaseResult {
    private boolean success = true;
    private String message;
    private String code;
}
