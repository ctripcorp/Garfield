package com.ctrip.garfield.process.orchestration;

import com.ctrip.garfield.common.context.GarfieldContext;
import com.ctrip.garfield.common.enums.ErrorCode;
import com.ctrip.garfield.common.exception.GarfieldException;
import com.ctrip.garfield.common.model.OperationResult;
import com.ctrip.garfield.common.enums.EngineResultCode;
import com.ctrip.garfield.common.spi.MetricsReporter;
import com.ctrip.garfield.common.spi.observation.ReadObservation;
import com.ctrip.garfield.process.StorageProcess;
import com.ctrip.garfield.process.capability.BatchReadable;
import com.ctrip.garfield.process.route.StorageRoute;
import com.ctrip.garfield.process.route.StorageRouteFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Read orchestrator with a single unified entry point.
 *
 * @author Trip.com Group
 */
public class ReadOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ReadOrchestrator.class);

    private final StorageRouteFactory routeFactory;
    private final LockOrchestrator lockOrchestrator;
    private final MetricsReporter metricsReporter;

    public ReadOrchestrator(StorageRouteFactory routeFactory, LockOrchestrator lockOrchestrator,
                            MetricsReporter metricsReporter) {
        this.routeFactory = routeFactory;
        this.lockOrchestrator = lockOrchestrator;
        this.metricsReporter = metricsReporter;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public <ResData> OperationResult<ResData> read(GarfieldContext context) {
        Objects.requireNonNull(context, "context must not be null");

        StorageRoute route = routeFactory.getRoute(context);

        StorageProcess process;
        if (context.getTargetEngineId() != null) {
            process = route.getByStorageId(context.getTargetEngineId());
            if (process == null) {
                log.warn("Specified engine not found: engineId={}, falling back to leader",
                        context.getTargetEngineId());
                process = route.getLeader();
            }
        } else {
            process = route.getLeader();
        }

        if (process == null) {
            log.error("No storage process available for read: reqClassName={}", context.getReqClassName());
            return OperationResult.error(EngineResultCode.ERROR,
                    new GarfieldException(ErrorCode.READ_FAILURE));
        }

        if (!(process instanceof BatchReadable readable)) {
            return OperationResult.error(EngineResultCode.ERROR,
                    new GarfieldException(ErrorCode.UNSUPPORTED_OPERATION));
        }

        if (context.isLockEnabled()) {
            boolean lockAcquired = lockOrchestrator.batchGetLocks(context);
            if (!lockAcquired) {
                return OperationResult.error(EngineResultCode.ERROR,
                        new GarfieldException(ErrorCode.LOCK_ACQUIRE_FAILURE));
            }
            context.removeUnlockedItems();
        }

        long startTime = System.currentTimeMillis();
        OperationResult<ResData> result = (OperationResult<ResData>) readable.read(context);
        long costMs = System.currentTimeMillis() - startTime;

        if (metricsReporter != null) {
            metricsReporter.recordRead(ReadObservation.builder()
                    .reqClassName(context.getReqClassName())
                    .engineId(process.getStorageEngineInstance().getStorageEngineConfig().getStorageId())
                    .storageType(process.getStorageEngineInstance().getStorageEngineConfig().getStorageType())
                    .operationType(context.getOperationType())
                    .resultCode(result.getResultCode())
                    .count(result.getData() != null ? result.getData().size() : 0)
                    .costMs(costMs)
                    .exception(result.getException())
                    .build());
        }
        return result;
    }
}
