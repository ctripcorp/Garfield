package com.ctrip.garfield.process.impl;

import com.ctrip.garfield.common.config.StorageProcessConfig;
import com.ctrip.garfield.common.context.GarfieldContext;
import com.ctrip.garfield.common.enums.OperationType;
import com.ctrip.garfield.common.model.BaseDataUnit;
import com.ctrip.garfield.common.model.BaseFailureResult;
import com.ctrip.garfield.common.model.OperationResult;
import com.ctrip.garfield.common.enums.EngineResultCode;
import com.ctrip.garfield.common.spi.RateLimiter;
import com.ctrip.garfield.engine.capability.HashCapable;
import com.ctrip.garfield.engine.capability.QueryCapable;
import com.ctrip.garfield.engine.capability.ScanCapable;
import com.ctrip.garfield.engine.wrapper.HashValueWrapper;
import com.ctrip.garfield.engine.wrapper.QueryRequest;
import com.ctrip.garfield.engine.wrapper.ScanRequest;
import com.ctrip.garfield.process.AbstractStorageProcess;
import com.ctrip.garfield.process.capability.BatchDeletable;
import com.ctrip.garfield.process.capability.BatchReadable;
import com.ctrip.garfield.process.capability.BatchWritable;
import com.ctrip.garfield.process.route.StorageEngineInstance;
import com.ctrip.garfield.transfer.HashKvTransfer;
import com.ctrip.garfield.transfer.ReadIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Storage process for hash-based engine operations.
 *
 * @author Trip.com Group
 */
public class HashStorageProcess<ReqData extends BaseDataUnit, ResData,
        Wrapper extends HashValueWrapper, FailureType extends BaseFailureResult>
        extends AbstractStorageProcess<ReqData, ResData, FailureType>
        implements BatchWritable<ReqData, FailureType>,
                   BatchReadable<ReqData, ResData, FailureType>,
                   BatchDeletable<ReqData, FailureType> {

    private static final Logger log = LoggerFactory.getLogger(HashStorageProcess.class);

    private final HashKvTransfer<ReqData, ResData, Wrapper> hashKvTransfer;
    private final HashCapable<Wrapper> hashCapable;

    @SuppressWarnings("unchecked")
    public HashStorageProcess(HashKvTransfer<ReqData, ResData, Wrapper> hashKvTransfer,
                              HashCapable<?> hashCapable,
                              StorageEngineInstance storageEngineInstance,
                              StorageProcessConfig storageProcessConfig,
                              RateLimiter rateLimiter) {
        super(storageEngineInstance, storageProcessConfig, rateLimiter);
        this.hashKvTransfer = hashKvTransfer;
        this.hashCapable = (HashCapable<Wrapper>) hashCapable;
    }

    @Override
    @SuppressWarnings("unchecked")
    public OperationResult<?> write(GarfieldContext<ReqData, FailureType> context) {
        OperationResult<?> result = initOperationResult();
        String commandKey = buildCommandKey(context);

        List<Wrapper> wrappers = new ArrayList<>();
        Map<Integer, Integer> indexMap = new HashMap<>();

        List<ReqData> dataInfos = context.getDataInfos();
        for (int i = 0; i < dataInfos.size(); i++) {
            ReqData data = dataInfos.get(i);
            if (!hashKvTransfer.filterBeforePut(context, data)) {
                continue;
            }
            try {
                Wrapper wrapper = hashKvTransfer.toStorage(data);
                wrapper.setLockEntity(data.getLockEntity());
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

        OperationResult<?> engineResult = hashCapable.hashPut(wrappers, commandKey);
        result.setResultCode(engineResult.getResultCode());
        result.setException(engineResult.getException());
        result.setActualExecuteSize(engineResult.getActualExecuteSize());

        List<FailureType> errorDetails = new ArrayList<>();
        for (int j = 0; j < wrappers.size(); j++) {
            Wrapper wrapper = wrappers.get(j);
            if (wrapper.getFailureType() != null) {
                Integer dataInfoIndex = indexMap.get(j);
                if (dataInfoIndex != null) {
                    ReqData data = dataInfos.get(dataInfoIndex);
                    BaseFailureResult failure = data.createFailureResult(wrapper.getFailureType());
                    failure.setDataIndex(dataInfoIndex);
                    errorDetails.add((FailureType) failure);
                }
            }
        }
        result.setErrorDetails(errorDetails);
        return result;
    }

    @Override
    public OperationResult<?> delete(GarfieldContext<ReqData, FailureType> context) {
        OperationResult<?> result = initOperationResult();
        String commandKey = buildCommandKey(context);

        List<Wrapper> wrappers = new ArrayList<>();
        for (ReqData data : context.getDataInfos()) {
            if (!hashKvTransfer.filterBeforePut(context, data)) {
                continue;
            }
            try {
                wrappers.add(hashKvTransfer.toStorage(data));
            } catch (IOException e) {
                log.error("Transfer toStorage failed for delete, aborting batch", e);
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

        OperationResult<?> engineResult = hashCapable.hashDelete(wrappers, commandKey);
        result.setResultCode(engineResult.getResultCode());
        result.setException(engineResult.getException());
        result.setActualExecuteSize(wrappers.size());
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public OperationResult<ResData> read(GarfieldContext<ReqData, FailureType> context) {
        ReadIntent<Wrapper> intent = hashKvTransfer.buildReadIntent(context);
        String commandKey = buildCommandKey(context);

        OperationType opType = switch (intent) {
            case ReadIntent.KeyLookup<?> kl -> OperationType.BATCH_GET;
            case ReadIntent.PrefixScan<?> ps -> OperationType.SCAN;
            case ReadIntent.IndexQuery<?> iq -> OperationType.QUERY;
        };
        context.setOperationType(opType);

        OperationResult<Wrapper> rawResult = switch (intent) {
            case ReadIntent.KeyLookup<Wrapper> kl -> {
                if (!checkRateLimit(context, kl.wrappers().size())) {
                    yield OperationResult.error(EngineResultCode.RATE_LIMIT_ERROR, null);
                }
                yield hashCapable.hashGet(kl.wrappers(), commandKey);
            }
            case ReadIntent.PrefixScan<Wrapper> ps -> {
                if (!(hashCapable instanceof ScanCapable<?>)) {
                    yield unsupportedRead();
                }
                if (!checkRateLimit(context, 1)) {
                    yield OperationResult.error(EngineResultCode.RATE_LIMIT_ERROR, null);
                }
                ScanRequest<Wrapper> scanReq = ps.request();
                if (context.getLimit() != null) {
                    scanReq.setLimit(context.getLimit());
                }
                if (context.getContinuationToken() != null) {
                    scanReq.setContinuationToken(context.getContinuationToken());
                }
                yield ((ScanCapable<Wrapper>) hashCapable).scan(scanReq, commandKey);
            }
            case ReadIntent.IndexQuery<Wrapper> iq -> {
                if (!(hashCapable instanceof QueryCapable<?>)) {
                    yield unsupportedRead();
                }
                if (!checkRateLimit(context, 1)) {
                    yield OperationResult.error(EngineResultCode.RATE_LIMIT_ERROR, null);
                }
                QueryRequest<Wrapper> queryReq = iq.request();
                if (context.getLimit() != null) {
                    queryReq.setLimit(context.getLimit());
                }
                if (context.getContinuationToken() != null) {
                    queryReq.setContinuationToken(context.getContinuationToken());
                }
                yield ((QueryCapable<Wrapper>) hashCapable).query(queryReq, commandKey);
            }
        };

        return mapHashGetResult(rawResult);
    }

    private OperationResult<ResData> mapHashGetResult(OperationResult<Wrapper> getResult) {
        if (getResult == null) {
            return OperationResult.error(EngineResultCode.ERROR, null);
        }
        if (!getResult.isSuccess()) {
            OperationResult<ResData> err = OperationResult.error(getResult.getResultCode(), getResult.getException());
            err.setNextToken(getResult.getNextToken());
            return err;
        }
        if (getResult.getData() == null) {
            OperationResult<ResData> empty = OperationResult.success(Collections.emptyList());
            empty.setNextToken(getResult.getNextToken());
            return empty;
        }

        List<ResData> results = new ArrayList<>();
        for (Wrapper wrapper : getResult.getData()) {
            try {
                List<ResData> converted = hashKvTransfer.storageToObject(wrapper);
                if (converted != null) {
                    results.addAll(converted);
                }
            } catch (IOException e) {
                log.error("Failed to convert hash storage wrapper to object", e);
            }
        }
        OperationResult<ResData> out = OperationResult.success(results);
        out.setNextToken(getResult.getNextToken());
        return out;
    }
}
