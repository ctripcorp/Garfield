package com.ctrip.garfield.process.orchestration;

import com.ctrip.garfield.common.config.RetryConfig;
import com.ctrip.garfield.common.context.GarfieldContext;
import com.ctrip.garfield.common.enums.ErrorCode;
import com.ctrip.garfield.common.enums.OperationType;
import com.ctrip.garfield.common.exception.GarfieldException;
import com.ctrip.garfield.common.exception.NoStorageRouteException;
import com.ctrip.garfield.common.model.BaseFailureResult;
import com.ctrip.garfield.common.enums.EngineResultCode;
import com.ctrip.garfield.common.model.OperationResult;
import com.ctrip.garfield.common.spi.MetricsReporter;
import com.ctrip.garfield.common.spi.FollowerExecutorProvider;
import com.ctrip.garfield.common.spi.observation.WriteObservation;
import com.ctrip.garfield.process.StorageProcess;
import com.ctrip.garfield.process.capability.BatchDeletable;
import com.ctrip.garfield.process.capability.BatchWritable;
import com.ctrip.garfield.process.capability.Touchable;
import com.ctrip.garfield.process.compensation.CompensationPublisher;
import com.ctrip.garfield.process.route.StorageRoute;
import com.ctrip.garfield.process.route.StorageRouteFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Core write orchestrator. Leader/follower write orchestration:
 * <ol>
 *   <li>Lock check (optional)</li>
 *   <li>Leader synchronous write + retry ({@link RetryConfig})</li>
 *   <li>Followers async write; publish compensation on failure</li>
 * </ol>
 *
 * <p>Dispatch model (post 2026-04-23 refactor): three entry methods batchPut / batchDelete / touch,
 * each dispatched via the corresponding mixin instanceof check; no more switch(OperationType).
 *
 * @author Trip.com Group
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class WriteOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(WriteOrchestrator.class);
    private static final long DEFAULT_FOLLOWER_TIMEOUT_MS = 5000L;

    private final StorageRouteFactory routeFactory;
    private final LockOrchestrator lockOrchestrator;
    private final CompensationPublisher compensationPublisher;
    private final MetricsReporter metricsReporter;
    private final RetryConfig retryConfig;
    private final FollowerExecutorProvider followerExecutorProvider;
    private final long followerTimeoutMs;

    public WriteOrchestrator(StorageRouteFactory routeFactory,
                             LockOrchestrator lockOrchestrator,
                             CompensationPublisher compensationPublisher,
                             MetricsReporter metricsReporter,
                             RetryConfig retryConfig,
                             FollowerExecutorProvider followerExecutorProvider,
                             long followerTimeoutMs) {
        this.routeFactory = Objects.requireNonNull(routeFactory, "routeFactory must not be null");
        this.lockOrchestrator = Objects.requireNonNull(lockOrchestrator, "lockOrchestrator must not be null");
        this.compensationPublisher = Objects.requireNonNull(compensationPublisher, "compensationPublisher must not be null");
        this.metricsReporter = metricsReporter;
        this.retryConfig = retryConfig != null ? retryConfig : new RetryConfig();
        this.followerExecutorProvider = Objects.requireNonNull(followerExecutorProvider, "followerExecutorProvider must not be null");
        this.followerTimeoutMs = followerTimeoutMs > 0 ? followerTimeoutMs : DEFAULT_FOLLOWER_TIMEOUT_MS;
    }

    public void shutdown() {
        followerExecutorProvider.shutdown();
    }


    public boolean batchPut(GarfieldContext context) {
        Objects.requireNonNull(context, "context must not be null");
        context.setOperationType(OperationType.BATCH_PUT);
        return dispatch(context, OperationType.BATCH_PUT,
                p -> ((BatchWritable) p).write(context),
                p -> p instanceof BatchWritable,
                null);
    }

    public boolean batchDelete(GarfieldContext context) {
        Objects.requireNonNull(context, "context must not be null");
        context.setOperationType(OperationType.BATCH_DELETE);
        return dispatch(context, OperationType.BATCH_DELETE,
                p -> ((BatchDeletable) p).delete(context),
                p -> p instanceof BatchDeletable,
                null);
    }

    /**
     * Touch semantics: EXPIREAT absolute timestamp. expireAtMs is passed through as a method
     * parameter; retry loops and compensation replays share the same value for strict idempotency.
     */
    public boolean touch(GarfieldContext context, long expireAtMs) {
        Objects.requireNonNull(context, "context must not be null");
        context.setOperationType(OperationType.TOUCH);
        return dispatch(context, OperationType.TOUCH,
                p -> ((Touchable) p).touch(context, expireAtMs),
                p -> p instanceof Touchable,
                expireAtMs);
    }

    public boolean batchPut(GarfieldContext context, String engineId) {
        Objects.requireNonNull(context, "context must not be null");
        return targeted(context, engineId, OperationType.BATCH_PUT,
                p -> ((BatchWritable) p).write(context),
                p -> p instanceof BatchWritable);
    }

    public boolean batchDelete(GarfieldContext context, String engineId) {
        Objects.requireNonNull(context, "context must not be null");
        return targeted(context, engineId, OperationType.BATCH_DELETE,
                p -> ((BatchDeletable) p).delete(context),
                p -> p instanceof BatchDeletable);
    }

    public boolean touch(GarfieldContext context, String engineId, long expireAtMs) {
        Objects.requireNonNull(context, "context must not be null");
        return targeted(context, engineId, OperationType.TOUCH,
                p -> ((Touchable) p).touch(context, expireAtMs),
                p -> p instanceof Touchable);
    }

    /**
     * Unified compensation replay entry. TOUCH is not supported here (requires expireAtMs);
     * consumers should call {@link #touch(GarfieldContext, String, long)} directly.
     */
    public boolean compensate(GarfieldContext context, String engineId, OperationType op) {
        Objects.requireNonNull(context, "context must not be null");
        if (op == null) {
            throw new IllegalArgumentException("OperationType must not be null for compensation");
        }
        switch (op) {
            case BATCH_PUT:    return batchPut(context, engineId);
            case BATCH_DELETE: return batchDelete(context, engineId);
            case TOUCH:
                throw new IllegalArgumentException(
                        "TOUCH compensation requires expireAtMs, call touch(ctx, engineId, expireAtMs) directly");
            default:
                throw new GarfieldException(ErrorCode.UNSUPPORTED_OPERATION,
                        "Compensation does not support " + op);
        }
    }


    /**
     * @param expireAtMs non-null only for TOUCH, used for compensation message serialization; null for other ops.
     */
    private boolean dispatch(GarfieldContext context,
                             OperationType op,
                             Function<StorageProcess, OperationResult> invoker,
                             java.util.function.Predicate<StorageProcess> capabilityCheck,
                             Long expireAtMs) {
        StorageRoute route = routeFactory.getRoute(context);

        if (!lockOrchestrator.batchCheckLocks(context)) {
            return false;
        }

        if (!runLeader(context, route, op, invoker, capabilityCheck)) {
            return false;
        }

        runFollowers(context, route, op, invoker, capabilityCheck, expireAtMs);
        return context.getResult().isSuccess();
    }

    private boolean targeted(GarfieldContext context,
                             String engineId,
                             OperationType op,
                             Function<StorageProcess, OperationResult> invoker,
                             java.util.function.Predicate<StorageProcess> capabilityCheck) {
        StorageRoute route = routeFactory.getRoute(context);
        StorageProcess process = route.getByStorageId(engineId);
        if (process == null) {
            throw new NoStorageRouteException(context.getReqClassName());
        }
        if (!capabilityCheck.test(process)) {
            throw new GarfieldException(ErrorCode.UNSUPPORTED_OPERATION);
        }

        OperationResult result = invoker.apply(process);
        if (!result.isSuccess()) {
            context.setOverallError(result.getResultCode().toErrorCode(op));
            populateErrorDetailsMap(context, result);
            return false;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private void populateErrorDetailsMap(GarfieldContext context, OperationResult result) {
        List errorDetails = result.getErrorDetails();
        if (errorDetails == null || errorDetails.isEmpty()) {
            return;
        }
        List dataInfos = context.getDataInfos();
        if (dataInfos == null) {
            return;
        }
        Map failedMap = new LinkedHashMap();
        for (Object detail : errorDetails) {
            BaseFailureResult failure = (BaseFailureResult) detail;
            int idx = failure.getDataIndex();
            if (idx >= 0 && idx < dataInfos.size()) {
                failedMap.put(dataInfos.get(idx), detail);
            } else if (idx == -1) {
                log.warn("BaseFailureResult.dataIndex not set (-1), failure detail will not appear in context.errorDetails. "
                        + "Ensure StorageProcess implementation calls failure.setDataIndex(). failureType={}",
                        failure.getFailureType());
            }
        }
        context.addErrorDetails(failedMap);
    }

    private boolean runLeader(GarfieldContext context,
                              StorageRoute route,
                              OperationType op,
                              Function<StorageProcess, OperationResult> invoker,
                              java.util.function.Predicate<StorageProcess> capabilityCheck) {
        StorageProcess leaderProcess = route.getLeader();
        if (leaderProcess == null) {
            context.setOverallError(ErrorCode.ROUTE_NOT_FOUND);
            return false;
        }
        if (!capabilityCheck.test(leaderProcess)) {
            context.setOverallError(ErrorCode.UNSUPPORTED_OPERATION);
            return false;
        }

        long startTime = System.currentTimeMillis();
        int actualAttempts = 0;
        int totalCount = context.getDataInfos() != null ? context.getDataInfos().size() : 0;
        OperationResult result = null;

        for (int attempt = 0; attempt < retryConfig.getMaxAttempts(); attempt++) {
            actualAttempts = attempt;
            result = invoker.apply(leaderProcess);

            if (!result.isNeedRetry()) {
                break;
            }

            if (attempt < retryConfig.getMaxAttempts() - 1) {
                log.info("Leader write needs retry, op={}, attempt={}/{}",
                        op, attempt + 1, retryConfig.getMaxAttempts());
                try {
                    Thread.sleep(retryConfig.getRetryIntervalMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        context.setWriteLeaderEndTimestamp(System.currentTimeMillis());

        long costMs = System.currentTimeMillis() - startTime;

        if (!result.isSuccess()) {
            context.setOverallError(result.getResultCode().toErrorCode(op));
            populateErrorDetailsMap(context, result);
            reportLeaderWrite(context, leaderProcess, op, result, costMs, actualAttempts, totalCount);
            return false;
        }

        if (result.getErrorDetails() != null && !result.getErrorDetails().isEmpty()) {
            List dataInfos = context.getDataInfos();
            int originalSize = dataInfos.size();

            Set<Integer> failedIndices = new HashSet<>();
            Map failedMap = new LinkedHashMap();
            for (Object detail : (List) result.getErrorDetails()) {
                BaseFailureResult failure = (BaseFailureResult) detail;
                int idx = failure.getDataIndex();
                if (idx >= 0 && idx < originalSize) {
                    failedIndices.add(idx);
                    failedMap.put(dataInfos.get(idx), detail);
                }
            }

            if (!failedMap.isEmpty()) {
                context.addErrorDetails(failedMap);

                List succeeded = new ArrayList(originalSize - failedIndices.size());
                for (int i = 0; i < originalSize; i++) {
                    if (!failedIndices.contains(i)) {
                        succeeded.add(dataInfos.get(i));
                    }
                }
                context.setDataInfos(succeeded);
            }
        }
        reportLeaderWrite(context, leaderProcess, op, result, costMs, actualAttempts, totalCount);
        return true;
    }

    private void runFollowers(GarfieldContext context,
                              StorageRoute route,
                              OperationType op,
                              Function<StorageProcess, OperationResult> invoker,
                              java.util.function.Predicate<StorageProcess> capabilityCheck,
                              Long expireAtMs) {
        List<StorageProcess> followers = route.getFollowers();
        if (followers == null || followers.isEmpty()) {
            return;
        }

        // context is effectively immutable at this point: leader write is complete,
        // dataInfos has been trimmed to successful items only, no further mutations expected.
        for (StorageProcess followerProcess : followers) {
            if (!followerProcess.getStorageEngineInstance().getStorageEngineConfig().isEnabled()) {
                continue;
            }
            if (!capabilityCheck.test(followerProcess)) {
                log.warn("Follower does not support op={}, skipping follower engine", op);
                continue;
            }

            String followerStorageId = followerProcess.getStorageEngineInstance()
                    .getStorageEngineConfig().getStorageId();

            try {
                AtomicBoolean done = new AtomicBoolean(false);

                CompletableFuture.runAsync(() -> {
                    try {
                        OperationResult result = invoker.apply(followerProcess);
                        if (!done.compareAndSet(false, true)) return;
                        if (result.isSuccess()) {
                            reportFollowerWrite(context, followerProcess, op, result);
                        } else {
                            log.warn("Follower write failed: storageId={}, op={}, resultCode={}",
                                    followerStorageId, op, result.getResultCode());
                            reportFollowerWrite(context, followerProcess, op, result);
                            compensationPublisher.sendCompensation(context, result, followerStorageId, op, expireAtMs);
                        }
                    } catch (Exception e) {
                        if (!done.compareAndSet(false, true)) return;
                        log.error("Follower write exception: storageId={}, op={}", followerStorageId, op, e);
                        OperationResult errorResult = OperationResult.error(EngineResultCode.ERROR, e);
                        reportFollowerWrite(context, followerProcess, op, errorResult);
                        compensationPublisher.sendCompensation(context, null, followerStorageId, op, expireAtMs);
                    }
                }, followerExecutorProvider.getExecutor(followerStorageId))
                .orTimeout(followerTimeoutMs, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    if (!done.compareAndSet(false, true)) return null;
                    Throwable cause = (ex instanceof CompletionException) ? ex.getCause() : ex;
                    if (cause instanceof TimeoutException) {
                        log.warn("Follower write timed out: storageId={}, timeoutMs={}", followerStorageId, followerTimeoutMs);
                    } else {
                        log.error("Follower write async failure: storageId={}", followerStorageId, cause);
                    }
                    OperationResult errorResult = OperationResult.error(EngineResultCode.ERROR,
                            cause instanceof Exception ? (Exception) cause : new RuntimeException(cause));
                    reportFollowerWrite(context, followerProcess, op, errorResult);
                    compensationPublisher.sendCompensation(context, null, followerStorageId, op, expireAtMs);
                    return null;
                });
            } catch (RejectedExecutionException e) {
                log.error("Follower executor rejected task: storageId={}", followerStorageId, e);
                OperationResult errorResult = OperationResult.error(EngineResultCode.ERROR, e);
                reportFollowerWrite(context, followerProcess, op, errorResult);
                compensationPublisher.sendCompensation(context, null, followerStorageId, op, expireAtMs);
            }
        }
    }

    private void reportLeaderWrite(GarfieldContext context, StorageProcess leaderProcess,
                                   OperationType op, OperationResult result,
                                   long costMs, int retryAttempts, int totalCount) {
        if (metricsReporter == null) {
            return;
        }
        metricsReporter.recordWrite(WriteObservation.builder()
                .reqClassName(context.getReqClassName())
                .engineId(leaderProcess.getStorageEngineInstance().getStorageEngineConfig().getStorageId())
                .storageType(leaderProcess.getStorageEngineInstance().getStorageEngineConfig().getStorageType())
                .operationType(op)
                .operationResult(result)
                .totalCount(totalCount)
                .costMs(costMs)
                .leader(true)
                .retryAttempts(retryAttempts)
                .lagMs(null)
                .build());
    }

    private void reportFollowerWrite(GarfieldContext context, StorageProcess followerProcess,
                                  OperationType op, OperationResult result) {
        if (metricsReporter == null) {
            return;
        }
        Long lagMs = null;
        long baseline = context.getWriteLeaderEndTimestamp();
        if (baseline > 0 && result.isSuccess()) {
            lagMs = System.currentTimeMillis() - baseline;
        }
        metricsReporter.recordWrite(WriteObservation.builder()
                .reqClassName(context.getReqClassName())
                .engineId(followerProcess.getStorageEngineInstance().getStorageEngineConfig().getStorageId())
                .storageType(followerProcess.getStorageEngineInstance().getStorageEngineConfig().getStorageType())
                .operationType(op)
                .operationResult(result)
                .totalCount(context.getDataInfos() != null ? context.getDataInfos().size() : 0)
                .costMs(lagMs != null ? lagMs : 0)
                .leader(false)
                .retryAttempts(0)
                .lagMs(lagMs)
                .build());
    }
}
