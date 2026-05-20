package com.ctrip.garfield.example.engine;

import com.ctrip.garfield.common.config.StorageEngineConfig;
import com.ctrip.garfield.common.enums.GarfieldStorageType;
import com.ctrip.garfield.common.enums.ProcessType;
import com.ctrip.garfield.common.enums.StorageType;
import com.ctrip.garfield.common.spi.CircuitBreaker;
import com.ctrip.garfield.common.spi.StorageEngine;
import com.ctrip.garfield.common.spi.StorageEngineFactory;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Properties;
import java.util.Set;

/**
 * Factory that creates Kafka message engine instances.
 *
 * @author Trip.com Group
 */
@Component
@RequiredArgsConstructor
public class KafkaEngineFactory implements StorageEngineFactory {

    private final CircuitBreaker circuitBreaker;

    @Override
    public StorageType storageType() {
        return GarfieldStorageType.KAFKA;
    }

    @Override
    public Set<ProcessType> supportedProcessTypes() {
        return EnumSet.of(ProcessType.MESSAGE);
    }

    @Override
    public StorageEngine createEngine(StorageEngineConfig config) {
        Object serversObj = config.getProperties().get("bootstrap.servers");
        if (serversObj == null) {
            throw new IllegalArgumentException("Missing required property 'bootstrap.servers' for KAFKA engine: storageId=" + config.getStorageId());
        }
        String bootstrapServers = (String) serversObj;
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        KafkaProducer<String, String> producer = new KafkaProducer<>(props);
        return new KafkaMessageEngine(producer, circuitBreaker);
    }
}
