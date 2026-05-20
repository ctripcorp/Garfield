package com.ctrip.garfield.common.spi.defaults;

import com.ctrip.garfield.common.model.CompensationMessage;
import com.ctrip.garfield.common.spi.CompensationChannel;
import lombok.extern.slf4j.Slf4j;

/**
 * No-op compensation channel that drops messages with a warning log.
 *
 * @author Trip.com Group
 */
@Slf4j
public class NoOpCompensationChannel implements CompensationChannel {

    @Override
    public void publish(CompensationMessage message) {
        log.warn("No compensation channel configured, dropping message for req={} storage={}",
                message.getReqClassName(), message.getStorageId());
    }
}
