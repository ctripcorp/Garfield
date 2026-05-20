package com.ctrip.garfield.engine.wrapper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Base wrapper for message-queue engines. Field semantics across MQ systems:
 *
 * <ul>
 *   <li>{@code topic}       – Kafka topic / RabbitMQ exchange / RocketMQ topic</li>
 *   <li>{@code messageKey}  – Kafka partition key / RabbitMQ routingKey / RocketMQ keys</li>
 *   <li>{@code messageBody} – payload</li>
 *   <li>{@code headers}     – additional attributes (native MQ headers or business extensions)</li>
 * </ul>
 *
 * @author Trip.com Group
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageWrapper {

    private String topic;
    private String messageKey;
    private String messageBody;
    private Map<String, String> headers;
}
