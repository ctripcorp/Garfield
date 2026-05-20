package com.ctrip.garfield.process.compensation;

import com.ctrip.garfield.common.context.CompensationContext;
import com.ctrip.garfield.common.model.CompensationMessage;
import com.ctrip.garfield.common.model.CompensationResult;
import com.ctrip.garfield.common.spi.BackoffStrategy;
import com.ctrip.garfield.common.spi.CompensationExhaustionHandler;
import com.ctrip.garfield.common.spi.MetricsReporter;
import com.ctrip.garfield.common.spi.observation.CompensationObservation;
import com.ctrip.garfield.process.orchestration.WriteOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates compensation message processing on the consumer side.
 *
 * <p><b>Pure function (2026-05-07 refactor)</b>: {@code process()} computes a
 * {@link CompensationResult} and returns it. It does <b>not</b> dispatch
 * retries — consumers must inspect {@code result.status} and arrange
 * re-delivery themselves (e.g. MQ-native retry exceptions, Kafka
 * scheduler + republish, local {@code DelayQueue.offer}). Failure to do so
 * will drop compensation messages.
 *
 * <p>Processing flow:
 * <ol>
 *   <li>Look up {@link CompensationHandler} by {@code reqClassName}</li>
 *   <li>If missing, return {@code PERMANENT_FAILURE}</li>
 *   <li>Call {@code handler.getCompensateDataList()} to extract data</li>
 *   <li>Call {@code handler.writeData()} to execute the compensation write</li>
 *   <li>On success: return {@code SUCCESS}</li>
 *   <li>On failure + not exhausted: compute delay via {@link BackoffStrategy},
 *       return {@code NEED_RETRY(delayMs, attempt)}</li>
 *   <li>On failure + exhausted: return {@code EXHAUSTED} — the consumer is
 *       responsible for invoking exhaustion hooks</li>
 * </ol>
 *
 * <p>This method never throws — it always returns a {@link CompensationResult}.
 *
 * @author Trip.com Group
 */
public class CompensationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(CompensationOrchestrator.class);
    private static final String METRIC_STATUS_NO_HANDLER = "NO_HANDLER";

    private final Map<String, CompensationHandler<?>> handlerRegistry = new ConcurrentHashMap<>();
    private final WriteOrchestrator writeOrchestrator;
    private final BackoffStrategy backoffStrategy;
    private final CompensationExhaustionHandler exhaustionHandler;
    private final MetricsReporter metricsReporter;

    public CompensationOrchestrator(WriteOrchestrator writeOrchestrator,
                                    BackoffStrategy backoffStrategy,
                                    CompensationExhaustionHandler exhaustionHandler,
                                    MetricsReporter metricsReporter) {
        this.writeOrchestrator = writeOrchestrator;
        this.backoffStrategy = backoffStrategy;
        this.exhaustionHandler = exhaustionHandler;
        this.metricsReporter = metricsReporter;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public CompensationResult process(CompensationMessage message, int attempt) {
        Objects.requireNonNull(message, "message must not be null");
        CompensationHandler handler = handlerRegistry.get(message.getReqClassName());
        if (handler == null) {
            log.warn("No CompensationHandler registered for reqClassName={}", message.getReqClassName());
            reportMetrics(message, attempt, METRIC_STATUS_NO_HANDLER, null, null);
            return CompensationResult.permanentFailure(
                    "No CompensationHandler registered for reqClassName=" + message.getReqClassName());
        }

        CompensationContext context = CompensationContext.fromMessage(message, attempt);
        Exception lastError = null;

        try {
            List dataList = handler.getCompensateDataList(context);
            if (dataList == null || dataList.isEmpty()) {
                reportMetrics(message, attempt, CompensationResult.Status.SUCCESS.name(), null, null);
                return CompensationResult.success();
            }
            context.setDataList(dataList);

            boolean success = handler.writeData(context, writeOrchestrator);
            if (success) {
                reportMetrics(message, attempt, CompensationResult.Status.SUCCESS.name(), null, null);
                return CompensationResult.success();
            }
        } catch (Exception e) {
            lastError = e;
            log.warn("Compensation failed for req={} storage={} attempt={}",
                    message.getReqClassName(), message.getStorageId(), attempt, e);
        }

        BackoffStrategy strategy = handler.backoffStrategy() != null
                ? handler.backoffStrategy()
                : this.backoffStrategy;
        int maxRetries = strategy.maxRetries();
        if (maxRetries >= 0 && attempt >= maxRetries) {
            reportMetrics(message, attempt, CompensationResult.Status.EXHAUSTED.name(), null, lastError);
            return CompensationResult.exhausted(attempt, lastError);
        }

        long delayMs = strategy.computeDelay(attempt);
        reportMetrics(message, attempt, CompensationResult.Status.NEED_RETRY.name(), delayMs, lastError);
        return CompensationResult.needRetry(delayMs, attempt, lastError);
    }

    public void registerHandler(String reqClassName, CompensationHandler<?> handler) {
        Objects.requireNonNull(reqClassName, "reqClassName must not be null");
        Objects.requireNonNull(handler, "handler must not be null");
        handlerRegistry.put(reqClassName, handler);
    }

    private void reportMetrics(CompensationMessage message, int attempt,
                               String resultStatus, Long nextDelayMs, Exception ex) {
        if (metricsReporter != null) {
            metricsReporter.recordCompensation(CompensationObservation.builder()
                    .reqClassName(message.getReqClassName())
                    .storageId(message.getStorageId())
                    .attempt(attempt)
                    .resultStatus(resultStatus)
                    .nextDelayMs(nextDelayMs)
                    .exception(ex)
                    .operationType(message.getOperationType())
                    .leaderWriteTimestamp(message.getLeaderWriteTimestamp())
                    .build());
        }
    }
}
