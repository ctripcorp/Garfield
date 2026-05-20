package com.ctrip.garfield.example.compensation;

import com.ctrip.garfield.common.model.CompensationMessage;
import com.ctrip.garfield.common.model.CompensationResult;
import com.ctrip.garfield.common.spi.GarfieldSerializer;
import com.ctrip.garfield.process.compensation.CompensationOrchestrator;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Example: Kafka compensation consumer. Delegates compensation computation
 * to {@link CompensationOrchestrator} and dispatches retries itself based on
 * the returned {@link CompensationResult}.
 *
 * <p><b>Retry dispatch (2026-05-07)</b>: Kafka has no native delayed delivery,
 * so this consumer owns a {@link ScheduledExecutorService} to delay the retry
 * and then republish to the same compensation topic with an incremented
 * {@code attempt} header. Example-grade simplification — the scheduler lives
 * in this JVM; a process restart loses any retry not yet triggered. In
 * production, use a dedicated delay topic / timer service.
 *
 * @author Trip.com Group
 */
@Slf4j
@Component
public class KafkaCompensationConsumer {

    private final CompensationOrchestrator orchestrator;
    private final GarfieldSerializer serializer;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ScheduledExecutorService scheduler;

    public KafkaCompensationConsumer(CompensationOrchestrator orchestrator,
                                     GarfieldSerializer serializer,
                                     KafkaTemplate<String, String> kafkaTemplate) {
        this.orchestrator = orchestrator;
        this.serializer = serializer;
        this.kafkaTemplate = kafkaTemplate;
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1,
                r -> { Thread t = new Thread(r, "garfield-kafka-compensation-retry"); t.setDaemon(true); return t; });
        this.scheduler = executor;
    }

    @KafkaListener(topics = KafkaCompensationConstants.COMPENSATION_TOPIC, groupId = "garfield-compensation-group")
    public void onMessage(ConsumerRecord<String, String> record) {
        CompensationMessage message = serializer.deserialize(record.value(), CompensationMessage.class);
        int attempt = extractAttempt(record);
        CompensationResult result = orchestrator.process(message, attempt);
        switch (result.getStatus()) {
            case NEED_RETRY -> scheduler.schedule(
                    () -> republish(message, attempt + 1),
                    Math.max(0L, result.getRetryDelayMs()), TimeUnit.MILLISECONDS);
            case EXHAUSTED -> log.error(
                    "Compensation exhausted after {} attempts for req={} storage={}",
                    result.getAttempt(), message.getReqClassName(), message.getStorageId(),
                    result.getLastError());
            case PERMANENT_FAILURE -> log.error(
                    "Compensation permanent failure for req={} storage={}",
                    message.getReqClassName(), message.getStorageId(),
                    result.getLastError());
            case SUCCESS -> { /* ACK */ }
        }
        // Deserialization / scheduling failures propagate — Spring-Kafka's
        // configured error handler (default: DefaultErrorHandler) decides
        // retry vs. DLQ. Do NOT swallow here: silent ACK on malformed
        // payload would drop messages with no trace.
    }

    private void republish(CompensationMessage message, int nextAttempt) {
        try {
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    KafkaCompensationConstants.COMPENSATION_TOPIC,
                    message.getStorageId(),
                    serializer.serializeToString(message));
            record.headers().add(KafkaCompensationConstants.ATTEMPT_HEADER,
                    ByteBuffer.allocate(4).putInt(nextAttempt).array());
            kafkaTemplate.send(record);
        } catch (Exception e) {
            log.error("KafkaCompensationConsumer republish failed for storage={} attempt={}",
                    message.getStorageId(), nextAttempt, e);
        }
    }

    /**
     * Reads the current attempt number from the Kafka message header.
     * <p>Returns {@code 1} for the first delivery (published via
     * {@code CompensationChannel.publish}, which carries no header).
     * Re-deliveries scheduled by this consumer include a header with
     * {@code attempt + 1}.
     */
    private int extractAttempt(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader(KafkaCompensationConstants.ATTEMPT_HEADER);
        if (header != null && header.value().length >= 4) {
            return ByteBuffer.wrap(header.value()).getInt();
        }
        return 1;
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
