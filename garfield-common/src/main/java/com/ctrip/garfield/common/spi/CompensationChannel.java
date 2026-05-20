package com.ctrip.garfield.common.spi;

import com.ctrip.garfield.common.model.CompensationMessage;

/**
 * SPI for publishing compensation messages when follower writes fail.
 *
 * <p>Implementations bridge to the actual message transport (Kafka, RabbitMQ,
 * local queue, etc.). The default {@code NoOpCompensationChannel} drops messages
 * with a warning log; users must provide a real implementation for eventual consistency.
 *
 * @author Trip.com Group
 */
public interface CompensationChannel {
    void publish(CompensationMessage message);
}
