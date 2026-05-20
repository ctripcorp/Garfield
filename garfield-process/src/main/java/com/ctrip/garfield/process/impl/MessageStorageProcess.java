package com.ctrip.garfield.process.impl;

import com.ctrip.garfield.common.config.StorageProcessConfig;
import com.ctrip.garfield.common.context.GarfieldContext;
import com.ctrip.garfield.common.model.BaseDataUnit;
import com.ctrip.garfield.common.model.BaseFailureResult;
import com.ctrip.garfield.common.enums.EngineResultCode;
import com.ctrip.garfield.common.model.OperationResult;
import com.ctrip.garfield.common.spi.RateLimiter;
import com.ctrip.garfield.engine.capability.MessageCapable;
import com.ctrip.garfield.engine.wrapper.MessageWrapper;
import com.ctrip.garfield.process.AbstractStorageProcess;
import com.ctrip.garfield.process.capability.BatchWritable;
import com.ctrip.garfield.process.route.StorageEngineInstance;
import com.ctrip.garfield.transfer.MqTransfer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Storage process for message queue engine operations.
 *
 * @author Trip.com Group
 */
public class MessageStorageProcess<ReqData extends BaseDataUnit, ResData,
        Wrapper extends MessageWrapper, FailureType extends BaseFailureResult>
        extends AbstractStorageProcess<ReqData, ResData, FailureType>
        implements BatchWritable<ReqData, FailureType> {

    private static final Logger log = LoggerFactory.getLogger(MessageStorageProcess.class);

    private final MqTransfer<ReqData, Wrapper> mqTransfer;
    private final MessageCapable<Wrapper> messageCapable;

    @SuppressWarnings("unchecked")
    public MessageStorageProcess(MqTransfer<ReqData, Wrapper> mqTransfer,
                                 MessageCapable<?> messageCapable,
                                 StorageEngineInstance storageEngineInstance,
                                 StorageProcessConfig storageProcessConfig,
                                 RateLimiter rateLimiter) {
        super(storageEngineInstance, storageProcessConfig, rateLimiter);
        this.mqTransfer = mqTransfer;
        this.messageCapable = (MessageCapable<Wrapper>) messageCapable;
    }

    @Override
    public OperationResult<?> write(GarfieldContext<ReqData, FailureType> context) {
        OperationResult<?> result = initOperationResult();
        String commandKey = buildCommandKey(context);

        List<Wrapper> wrappers = new ArrayList<>();
        Map<Integer, Integer> indexMap = new HashMap<>();

        List<ReqData> dataInfos = context.getDataInfos();
        for (int i = 0; i < dataInfos.size(); i++) {
            ReqData data = dataInfos.get(i);
            if (!mqTransfer.filterBeforePut(context, data)) {
                continue;
            }
            try {
                Wrapper wrapper = mqTransfer.toStorage(data);
                indexMap.put(wrappers.size(), i);
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

        OperationResult<?> engineResult = messageCapable.sendMessage(wrappers, commandKey);
        result.setResultCode(engineResult.getResultCode());
        result.setException(engineResult.getException());
        result.setActualExecuteSize(engineResult.getActualExecuteSize());
        return result;
    }
}
