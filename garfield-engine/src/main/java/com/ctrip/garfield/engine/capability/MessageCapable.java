package com.ctrip.garfield.engine.capability;

import com.ctrip.garfield.common.model.OperationResult;
import com.ctrip.garfield.engine.wrapper.MessageWrapper;

import java.util.List;

/**
 * Engine capability for message/event publishing (e.g. Kafka produce).
 * Engines that support {@link com.ctrip.garfield.common.enums.ProcessType#MESSAGE} must implement this.
 *
 * @author Trip.com Group
 */
public interface MessageCapable<T extends MessageWrapper> {

    OperationResult<?> sendMessage(List<T> wrappers, String commandKey);
}
