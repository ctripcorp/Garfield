package com.ctrip.garfield.common.context;

import com.ctrip.garfield.common.enums.OperationType;
import com.ctrip.garfield.common.model.CompensationMessage;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Runtime context for a single compensation retry attempt.
 *
 * @author Trip.com Group
 */
@Getter
public class CompensationContext<DataInfo> {

    private String reqClassName;
    private String requestData;
    private String errorDetails;
    private String storageId;
    private String traceId;
    private OperationType operationType;
    private Long expireAtMs;

    @Setter
    private List<DataInfo> dataList;
    @Setter
    private int attempt;

    public static <T> CompensationContext<T> fromMessage(CompensationMessage message, int attempt) {
        CompensationContext<T> ctx = new CompensationContext<>();
        ctx.reqClassName = message.getReqClassName();
        ctx.requestData = message.getRequestData();
        ctx.errorDetails = message.getErrorDetails();
        ctx.storageId = message.getStorageId();
        ctx.traceId = message.getTraceId();
        ctx.operationType = message.getOperationType();
        ctx.expireAtMs = message.getExpireAtMs();
        ctx.attempt = attempt;
        return ctx;
    }
}
