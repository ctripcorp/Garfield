package com.ctrip.garfield.process.impl;

import com.ctrip.garfield.common.config.StorageProcessConfig;
import com.ctrip.garfield.common.context.GarfieldContext;
import com.ctrip.garfield.common.model.BaseFailureResult;
import com.ctrip.garfield.common.model.OperationResult;
import com.ctrip.garfield.common.enums.EngineResultCode;
import com.ctrip.garfield.common.spi.RateLimiter;
import com.ctrip.garfield.engine.capability.ServiceCallCapable;
import com.ctrip.garfield.process.AbstractStorageProcess;
import com.ctrip.garfield.process.capability.BatchReadable;
import com.ctrip.garfield.process.capability.BatchWritable;
import com.ctrip.garfield.process.route.StorageEngineInstance;
import com.ctrip.garfield.transfer.ServiceTransfer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Storage process for RPC/service call engine operations.
 *
 * @param <W> request wrapper type accepted by the engine
 * @author Trip.com Group
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class ServiceStorageProcess<ReqData, ResData, FailureType extends BaseFailureResult, W>
        extends AbstractStorageProcess<ReqData, ResData, FailureType>
        implements BatchWritable<ReqData, FailureType>,
                   BatchReadable<ReqData, ResData, FailureType> {

    private static final Logger log = LoggerFactory.getLogger(ServiceStorageProcess.class);

    private final ServiceTransfer<ReqData, FailureType, W> serviceTransfer;
    private final ServiceCallCapable<W> serviceCallCapable;

    public ServiceStorageProcess(ServiceTransfer<ReqData, FailureType, W> serviceTransfer,
                                 ServiceCallCapable<W> serviceCallCapable,
                                 StorageEngineInstance storageEngineInstance,
                                 StorageProcessConfig storageProcessConfig,
                                 RateLimiter rateLimiter) {
        super(storageEngineInstance, storageProcessConfig, rateLimiter);
        this.serviceTransfer = serviceTransfer;
        this.serviceCallCapable = serviceCallCapable;
    }

    @Override
    public OperationResult<?> write(GarfieldContext<ReqData, FailureType> context) {
        OperationResult<?> result = initOperationResult();
        String commandKey = buildCommandKey(context);

        if (!serviceTransfer.filterBeforePut(context)) {
            return result;
        }

        if (!checkRateLimit(context, 1)) {
            result.setResultCode(EngineResultCode.RATE_LIMIT_ERROR);
            return result;
        }

        try {
            W requestWrapper = serviceTransfer.toServiceRequest(context);
            if (requestWrapper == null) {
                return result;
            }
            OperationResult<?> engineResult = serviceCallCapable.invoke(requestWrapper, commandKey);
            result.setResultCode(engineResult.getResultCode());
            result.setException(engineResult.getException());
            result.setActualExecuteSize(engineResult.getActualExecuteSize());
        } catch (Exception e) {
            log.error("Service invocation failed", e);
            result.setResultCode(EngineResultCode.ERROR);
            result.setException(e);
        }
        return result;
    }

    @Override
    public OperationResult<ResData> read(GarfieldContext<ReqData, FailureType> context) {
        String commandKey = buildCommandKey(context);

        if (!checkRateLimit(context, 1)) {
            return OperationResult.error(EngineResultCode.RATE_LIMIT_ERROR, null);
        }

        try {
            W requestWrapper = serviceTransfer.toServiceRequest(context);
            if (requestWrapper == null) {
                return OperationResult.success(null);
            }
            OperationResult<?> engineResult = serviceCallCapable.invoke(requestWrapper, commandKey);
            if (!engineResult.isSuccess()) {
                return OperationResult.error(engineResult.getResultCode(), engineResult.getException());
            }
            return OperationResult.success(null);
        } catch (Exception e) {
            log.error("Service read invocation failed", e);
            return OperationResult.error(EngineResultCode.ERROR, e);
        }
    }
}
