package com.ctrip.garfield.process.impl;

import com.ctrip.garfield.common.config.StorageProcessConfig;
import com.ctrip.garfield.common.context.GarfieldContext;
import com.ctrip.garfield.common.model.BaseDataUnit;
import com.ctrip.garfield.common.model.BaseFailureResult;
import com.ctrip.garfield.common.model.OperationResult;
import com.ctrip.garfield.common.enums.EngineResultCode;
import com.ctrip.garfield.common.spi.RateLimiter;
import com.ctrip.garfield.engine.capability.TouchCapable;
import com.ctrip.garfield.engine.wrapper.KvValueWrapper;
import com.ctrip.garfield.process.AbstractStorageProcess;
import com.ctrip.garfield.process.capability.Touchable;
import com.ctrip.garfield.process.route.StorageEngineInstance;
import com.ctrip.garfield.transfer.KvTransfer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Storage process for expiration refresh engine operations.
 *
 * @author Trip.com Group
 */
public class TouchStorageProcess<ReqData extends BaseDataUnit,
        Wrapper extends KvValueWrapper, FailureType extends BaseFailureResult>
        extends AbstractStorageProcess<ReqData, Void, FailureType>
        implements Touchable<ReqData, FailureType> {

    private static final Logger log = LoggerFactory.getLogger(TouchStorageProcess.class);

    private final KvTransfer<ReqData, ?, Wrapper> kvTransfer;
    private final TouchCapable<Wrapper> touchCapable;

    @SuppressWarnings("unchecked")
    public TouchStorageProcess(KvTransfer<ReqData, ?, Wrapper> kvTransfer,
                               TouchCapable<?> touchCapable,
                               StorageEngineInstance storageEngineInstance,
                               StorageProcessConfig storageProcessConfig,
                               RateLimiter rateLimiter) {
        super(storageEngineInstance, storageProcessConfig, rateLimiter);
        this.kvTransfer = kvTransfer;
        this.touchCapable = (TouchCapable<Wrapper>) touchCapable;
    }

    @Override
    public OperationResult<?> touch(GarfieldContext<ReqData, FailureType> context, long expireAtMs) {
        OperationResult<?> result = initOperationResult();
        String commandKey = buildCommandKey(context);

        List<Wrapper> wrappers = new ArrayList<>();

        List<ReqData> dataInfos = context.getDataInfos();
        for (int i = 0; i < dataInfos.size(); i++) {
            ReqData data = dataInfos.get(i);
            if (!kvTransfer.filterBeforePut(context, data)) {
                continue;
            }

            try {
                Wrapper wrapper = kvTransfer.toStorage(data);
                wrapper.setLockEntity(data.getLockEntity());
                wrappers.add(wrapper);
            } catch (IOException e) {
                log.error("Transfer toStorage failed at index={}, aborting batch", i, e);
                return OperationResult.error(EngineResultCode.ERROR, e);
            }
        }

        if (wrappers.isEmpty()) {
            return result;
        }

        if (!checkRateLimit(context, wrappers.size())) {
            result.setResultCode(EngineResultCode.RATE_LIMIT_ERROR);
            return result;
        }

        OperationResult<?> engineResult = touchCapable.touch(wrappers, expireAtMs, commandKey);
        result.setResultCode(engineResult.getResultCode());
        result.setException(engineResult.getException());
        result.setActualExecuteSize(engineResult.getActualExecuteSize());
        return result;
    }
}
