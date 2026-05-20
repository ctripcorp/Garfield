package com.ctrip.garfield.example.compensation;

import com.ctrip.garfield.common.model.CompensationMessage;
import com.ctrip.garfield.common.model.CompensationResult;
import com.ctrip.garfield.common.spi.GarfieldSerializer;
import com.ctrip.garfield.process.compensation.CompensationOrchestrator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaCompensationConsumerTest {

    @Mock CompensationOrchestrator orchestrator;
    @Mock GarfieldSerializer serializer;
    @SuppressWarnings("rawtypes")
    @Mock KafkaTemplate kafkaTemplate;

    KafkaCompensationConsumer consumer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        consumer = new KafkaCompensationConsumer(orchestrator, serializer, kafkaTemplate);
    }

    @Test
    void onMessage_needRetry_schedulesRepublishWithIncrementedAttempt() throws Exception {
        CompensationMessage msg = CompensationMessage.builder()
                .reqClassName("OrderDataUnit").storageId("order-kv").build();
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                KafkaCompensationConstants.COMPENSATION_TOPIC, 0, 0L, "order-kv", "payload");

        when(serializer.deserialize("payload", CompensationMessage.class)).thenReturn(msg);
        when(serializer.serializeToString(msg)).thenReturn("payload");
        when(orchestrator.process(eq(msg), eq(1)))
                .thenReturn(CompensationResult.needRetry(50L, 1, null));

        consumer.onMessage(record);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate, timeout(500)).send(captor.capture());

        ProducerRecord<String, String> republished = captor.getValue();
        assertEquals(KafkaCompensationConstants.COMPENSATION_TOPIC, republished.topic());
        assertEquals("order-kv", republished.key());
        byte[] hv = republished.headers().lastHeader(KafkaCompensationConstants.ATTEMPT_HEADER).value();
        assertEquals(2, ByteBuffer.wrap(hv).getInt());
    }

    @Test
    void onMessage_success_doesNotRepublish() throws Exception {
        CompensationMessage msg = CompensationMessage.builder().storageId("x").build();
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                KafkaCompensationConstants.COMPENSATION_TOPIC, 0, 0L, "x", "p");
        when(serializer.deserialize("p", CompensationMessage.class)).thenReturn(msg);
        when(orchestrator.process(eq(msg), eq(1)))
                .thenReturn(CompensationResult.success());

        consumer.onMessage(record);

        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void onMessage_exhausted_doesNotRepublish() throws Exception {
        CompensationMessage msg = CompensationMessage.builder().storageId("x").build();
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                KafkaCompensationConstants.COMPENSATION_TOPIC, 0, 0L, "x", "p");
        when(serializer.deserialize("p", CompensationMessage.class)).thenReturn(msg);
        when(orchestrator.process(eq(msg), eq(1)))
                .thenReturn(CompensationResult.exhausted(3, new RuntimeException("boom")));

        consumer.onMessage(record);

        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
    }

    @Test
    void onMessage_permanentFailure_doesNotRepublish() throws Exception {
        CompensationMessage msg = CompensationMessage.builder().storageId("x").build();
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                KafkaCompensationConstants.COMPENSATION_TOPIC, 0, 0L, "x", "p");
        when(serializer.deserialize("p", CompensationMessage.class)).thenReturn(msg);
        when(orchestrator.process(eq(msg), eq(1)))
                .thenReturn(CompensationResult.permanentFailure("No handler"));

        consumer.onMessage(record);

        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
    }

    @Test
    void onMessage_readsAttemptHeader_andPassesToOrchestrator() throws Exception {
        CompensationMessage msg = CompensationMessage.builder().storageId("x").build();
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                KafkaCompensationConstants.COMPENSATION_TOPIC, 0, 0L, "x", "p");
        record.headers().add(new RecordHeader(
                KafkaCompensationConstants.ATTEMPT_HEADER,
                ByteBuffer.allocate(4).putInt(5).array()));

        when(serializer.deserialize("p", CompensationMessage.class)).thenReturn(msg);
        when(orchestrator.process(eq(msg), eq(5)))
                .thenReturn(CompensationResult.success());

        consumer.onMessage(record);

        verify(orchestrator).process(eq(msg), eq(5));
    }
}
