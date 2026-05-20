package com.ctrip.garfield.example.engine;

import com.ctrip.garfield.common.enums.GarfieldStorageType;
import com.ctrip.garfield.common.model.OperationResult;
import com.ctrip.garfield.common.spi.CircuitBreaker;
import com.ctrip.garfield.engine.base.AbstractMessageEngine;
import com.ctrip.garfield.example.model.OrderMessageWrapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.util.List;

/**
 * Kafka-based message engine for order event publishing.
 *
 * @author Trip.com Group
 */
public class KafkaMessageEngine extends AbstractMessageEngine<OrderMessageWrapper> {

    private final KafkaProducer<String, String> producer;

    public KafkaMessageEngine(KafkaProducer<String, String> producer, CircuitBreaker circuitBreaker) {
        super(circuitBreaker);
        this.producer = producer;
    }

    @Override
    public String getStorageType() {
        return GarfieldStorageType.KAFKA.name();
    }

    @Override
    protected OperationResult<?> doSendMessage(List<OrderMessageWrapper> wrappers) {
        for (OrderMessageWrapper wrapper : wrappers) {
            producer.send(new ProducerRecord<>(
                    wrapper.getTopic(),
                    wrapper.getMessageKey(),
                    wrapper.getMessageBody()));
        }
        return new OperationResult<>();
    }
}
