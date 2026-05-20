package com.ctrip.garfield.process;

import com.ctrip.garfield.common.context.GarfieldContext;
import com.ctrip.garfield.common.enums.ErrorCode;
import com.ctrip.garfield.common.exception.GarfieldException;
import com.ctrip.garfield.common.model.BaseFailureResult;
import com.ctrip.garfield.common.model.OperationResult;
import com.ctrip.garfield.common.enums.EngineResultCode;
import com.ctrip.garfield.common.config.StorageProcessConfig;
import com.ctrip.garfield.common.spi.RateLimitRequest;
import com.ctrip.garfield.common.spi.RateLimiter;
import com.ctrip.garfield.process.route.StorageEngineInstance;

import java.util.Locale;

/**
 * Base class for storage processes with rate limiting support.
 *
 * @author Trip.com Group
 */
public abstract class AbstractStorageProcess<ReqData, ResData, FailureType extends BaseFailureResult>
        implements StorageProcess<ReqData, ResData, FailureType> {

    protected final StorageEngineInstance storageEngineInstance;
    protected final StorageProcessConfig storageProcessConfig;
    protected final RateLimiter rateLimiter;

    protected AbstractStorageProcess(StorageEngineInstance storageEngineInstance,
                                     StorageProcessConfig storageProcessConfig,
                                     RateLimiter rateLimiter) {
        this.storageEngineInstance = storageEngineInstance;
        this.storageProcessConfig = storageProcessConfig;
        this.rateLimiter = rateLimiter;
    }

    protected boolean checkRateLimit(GarfieldContext<ReqData, ?> context, int operationCount) {
        String storageId = storageEngineInstance.getStorageEngineConfig().getStorageId();
        RateLimitRequest request = new RateLimitRequest(
                context.getReqClassName(),
                context.getOperationType(),
                storageId,
                operationCount,
                context.getRateLimitKey());
        return rateLimiter.tryAcquire(request);
    }

    protected String buildCommandKey(GarfieldContext<ReqData, FailureType> context) {
        String storageId = storageEngineInstance.getStorageEngineConfig().getStorageId();
        return context.getReqClassName().toLowerCase(Locale.ROOT) + "." + storageId.toLowerCase(Locale.ROOT);
    }

    protected OperationResult<?> initOperationResult() {
        OperationResult<?> result = new OperationResult<>();
        result.setResultCode(EngineResultCode.SUCCESS);
        return result;
    }

    @Override
    public StorageEngineInstance getStorageEngineInstance() {
        return storageEngineInstance;
    }

    protected OperationResult<?> unsupportedWrite() {
        OperationResult<?> r = initOperationResult();
        r.setResultCode(EngineResultCode.ERROR);
        r.setException(new GarfieldException(ErrorCode.UNSUPPORTED_OPERATION));
        return r;
    }

    protected <T> OperationResult<T> unsupportedRead() {
        return OperationResult.error(EngineResultCode.ERROR,
                new GarfieldException(ErrorCode.UNSUPPORTED_OPERATION));
    }
}
