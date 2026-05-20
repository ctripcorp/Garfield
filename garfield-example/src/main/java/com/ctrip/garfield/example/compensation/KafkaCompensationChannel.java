package com.ctrip.garfield.example.compensation;

import com.ctrip.garfield.common.model.CompensationMessage;
import com.ctrip.garfield.common.spi.CompensationChannel;
import com.ctrip.garfield.common.spi.GarfieldSerializer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * Kafka-based compensation channel that publishes failed writes for retry.
 *
 * @author Trip.com Group
 */
@Slf4j
@Component
public class KafkaCompensationChannel implements CompensationChannel {

    private final KafkaProducer<String, String> producer;
    private final GarfieldSerializer serializer;

    public KafkaCompensationChannel(
            @Value("${garfield.compensation.bootstrap-servers:localhost:9092}") String bootstrapServers,
            GarfieldSerializer serializer) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        this.producer = new KafkaProducer<>(props);
        this.serializer = serializer;
    }

    @Override
    public void publish(CompensationMessage message) {
        try {
            String json = serializer.serializeToString(message);
            producer.send(new ProducerRecord<>(KafkaCompensationConstants.COMPENSATION_TOPIC, message.getStorageId(), json),
                    (metadata, exception) -> {
                        if (exception != null) {
                            log.error("Failed to send compensation message for storage={}",
                                    message.getStorageId(), exception);
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to serialize compensation message for storage={}",
                    message.getStorageId(), e);
        }
    }

    @PreDestroy
    public void close() {
        producer.close();
    }
}
