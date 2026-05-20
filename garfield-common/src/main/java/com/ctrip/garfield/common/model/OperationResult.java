package com.ctrip.garfield.common.model;

import com.ctrip.garfield.common.enums.EngineResultCode;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Unified result for both read and write storage engine operations.
 *
 * <p>Read path uses {@code data} and {@code nextToken}; write path uses {@code errorDetails}.
 *
 * @param <T> the read data element type (use {@code Void} or {@code ?} for write-only results)
 * @author Trip.com Group
 */
@Data
public class OperationResult<T> {
    // Common
    private EngineResultCode resultCode = EngineResultCode.SUCCESS;
    private Exception exception;
    private Integer actualExecuteSize;

    // Read path
    private List<T> data = new ArrayList<>();
    private String nextToken;

    // Write path
    private List<? extends BaseFailureResult> errorDetails = new ArrayList<>();

    public boolean isSuccess() {
        return resultCode == EngineResultCode.SUCCESS;
    }

    public boolean isNeedRetry() {
        return resultCode == EngineResultCode.ERROR;
    }

    public static <T> OperationResult<T> success(List<T> data) {
        OperationResult<T> r = new OperationResult<>();
        r.setData(data);
        return r;
    }

    public static <T> OperationResult<T> error(EngineResultCode code, Exception ex) {
        OperationResult<T> r = new OperationResult<>();
        r.setResultCode(code);
        r.setException(ex);
        return r;
    }
}
