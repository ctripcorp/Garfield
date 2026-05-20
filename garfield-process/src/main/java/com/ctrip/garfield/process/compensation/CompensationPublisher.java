package com.ctrip.garfield.process.compensation;

import com.ctrip.garfield.common.context.GarfieldContext;
import com.ctrip.garfield.common.enums.OperationType;
import com.ctrip.garfield.common.model.CompensationMessage;
import com.ctrip.garfield.common.model.OperationResult;
import com.ctrip.garfield.common.spi.CompensationChannel;
import com.ctrip.garfield.common.spi.GarfieldSerializer;
import com.ctrip.garfield.common.spi.MetricsReporter;
import com.ctrip.garfield.common.spi.observation.CompensationObservation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds and publishes compensation messages when follower writes fail.
 *
 * @author Trip.com Group
 */
public class CompensationPublisher {

    private static final Logger log = LoggerFactory.getLogger(CompensationPublisher.class);

    private final CompensationChannel compensationChannel;
    private final GarfieldSerializer serializer;
    private final MetricsReporter metricsReporter;

    public CompensationPublisher(CompensationChannel compensationChannel,
                                 GarfieldSerializer serializer,
                                 MetricsReporter metricsReporter) {
        this.compensationChannel = compensationChannel;
        this.serializer = serializer;
        this.metricsReporter = metricsReporter;
    }

    @SuppressWarnings("rawtypes")
    @Deprecated
    public void sendCompensation(GarfieldContext context, OperationResult result, String storageId) {
        sendCompensation(context, result, storageId,
                context != null ? context.getOperationType() : null, null);
    }

    @SuppressWarnings("rawtypes")
    public void sendCompensation(GarfieldContext context,
                                 OperationResult result,
                                 String storageId,
                                 OperationType op,
                                 Long expireAtMs) {
        try {
            CompensationMessage message = CompensationMessage.builder()
                    .reqClassName(context.getReqClassName())
                    .requestData(serializer.serializeToString(context.getDataInfos()))
                    .errorDetails(result != null && result.getErrorDetails() != null
                            ? serializer.serializeToString(result.getErrorDetails())
                            : null)
                    .storageId(storageId)
                    .traceId(context.getTraceId())
                    .operationType(op)
                    .expireAtMs(expireAtMs)
                    .leaderWriteTimestamp(context.getWriteLeaderEndTimestamp() > 0
                            ? context.getWriteLeaderEndTimestamp() : null)
                    .build();
            compensationChannel.publish(message);
            reportCompensationPublish(context, storageId, op, null);
        } catch (Exception e) {
            log.error("Failed to publish compensation message for reqClassName={}, storageId={}, op={}",
                    context.getReqClassName(), storageId, op, e);
            reportCompensationPublish(context, storageId, op, e);
        }
    }

    @SuppressWarnings("rawtypes")
    private void reportCompensationPublish(GarfieldContext context, String storageId,
                                           OperationType op, Exception failure) {
        if (metricsReporter == null) {
            return;
        }
        try {
            metricsReporter.recordCompensation(CompensationObservation.builder()
                    .reqClassName(context.getReqClassName())
                    .storageId(storageId)
                    .attempt(0)
                    .resultStatus(failure == null ? "PUBLISHED" : "PUBLISH_FAILURE")
                    .exception(failure)
                    .operationType(op)
                    .leaderWriteTimestamp(context.getWriteLeaderEndTimestamp() > 0
                            ? context.getWriteLeaderEndTimestamp() : null)
                    .build());
        } catch (Exception reportEx) {
            log.warn("MetricsReporter.recordCompensation failed while reporting publish result", reportEx);
        }
    }
}
