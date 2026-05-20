package com.ctrip.garfield.process.compensation;

import com.ctrip.garfield.common.context.GarfieldContext;
import com.ctrip.garfield.common.enums.ErrorCode;
import com.ctrip.garfield.common.enums.OperationType;
import com.ctrip.garfield.common.exception.GarfieldException;
import com.ctrip.garfield.common.model.BaseFailureResult;
import com.ctrip.garfield.common.context.CompensationContext;
import com.ctrip.garfield.common.spi.BackoffStrategy;
import com.ctrip.garfield.process.orchestration.WriteOrchestrator;

import java.util.List;

/**
 * User-facing SPI for compensation message handling per business type.
 *
 * @author Trip.com Group
 */
public interface CompensationHandler<DataInfo> {

    /**
     * The routing key used to match this handler with incoming compensation messages.
     * Must match the {@code reqClassName} in the storage routing configuration.
     * Implementations may read from external config (e.g., Apollo or any external config source) to support runtime changes.
     *
     * @return non-null routing key string
     */
    String reqClassName();

    default BackoffStrategy backoffStrategy() { return null; }

    /**
     * Business-level exhaustion hook: terminal action triggered per reqClassName
     * (e.g. write to dead-letter table, mark order as abnormal, trigger reconciliation, notify upstream).
     *
     * <p>Invoked by {@code CompensationOrchestrator} in the exhaustion branch, <b>before</b>
     * {@code CompensationExhaustionHandler}. Exceptions thrown by this hook are swallowed
     * (logged only) and do not affect the platform-level {@code CompensationExhaustionHandler}
     * or metrics execution.
     *
     * <p>Default no-op — override only when the business side requires a terminal action.
     *
     * @param context   the compensation context (contains reqClassName / storageId / traceId / attempt, etc.)
     * @param lastError the last exception from a failed compensation attempt; {@code null} if {@code writeData} returned false without throwing
     */
    default void onExhausted(CompensationContext<DataInfo> context, Exception lastError) {}

    List<DataInfo> getCompensateDataList(CompensationContext<DataInfo> context) throws Exception;

    default boolean writeData(CompensationContext<DataInfo> context,
                              WriteOrchestrator writeOrchestrator) {
        GarfieldContext<DataInfo, BaseFailureResult> garfieldContext = new GarfieldContext<>();
        garfieldContext.setDataInfos(context.getDataList());
        garfieldContext.setReqClassName(context.getReqClassName());
        garfieldContext.setTraceId(context.getTraceId());

        OperationType op = context.getOperationType();
        if (op == null) {
            op = OperationType.BATCH_PUT;
        }
        switch (op) {
            case BATCH_PUT:
            case BATCH_DELETE:
                return writeOrchestrator.compensate(garfieldContext, context.getStorageId(), op);
            case TOUCH:
                Long expireAtMs = context.getExpireAtMs();
                if (expireAtMs == null) {
                    throw new GarfieldException(ErrorCode.UNSUPPORTED_OPERATION,
                            "TOUCH compensation requires expireAtMs");
                }
                return writeOrchestrator.touch(garfieldContext, context.getStorageId(), expireAtMs);
            default:
                throw new GarfieldException(ErrorCode.UNSUPPORTED_OPERATION,
                        "Compensation does not support " + op);
        }
    }
}
