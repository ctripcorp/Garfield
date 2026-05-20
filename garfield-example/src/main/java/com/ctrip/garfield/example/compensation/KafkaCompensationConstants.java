package com.ctrip.garfield.example.compensation;

/**
 * Shared constants for the Kafka compensation example
 * ({@link KafkaCompensationChannel} publisher,
 * {@link KafkaCompensationConsumer} consumer,
 * {@link KafkaCompensationConsumer} retry republisher).
 */
final class KafkaCompensationConstants {

    static final String COMPENSATION_TOPIC = "garfield-compensation";
    static final String ATTEMPT_HEADER = "garfield-attempt";

    private KafkaCompensationConstants() {
    }
}
