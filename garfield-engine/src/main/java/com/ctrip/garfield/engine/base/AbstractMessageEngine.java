package com.ctrip.garfield.engine.base;

import com.ctrip.garfield.common.model.OperationResult;
import com.ctrip.garfield.common.enums.EngineResultCode;
import com.ctrip.garfield.common.spi.CircuitBreaker;
import com.ctrip.garfield.common.spi.StorageEngine;
import com.ctrip.garfield.engine.capability.MessageCapable;
import com.ctrip.garfield.engine.wrapper.MessageWrapper;

import java.util.List;

/**
 * Template base class for message engines. Wraps {@code sendMessage} with
 * {@link CircuitBreaker} so that subclasses only implement {@code doSendMessage}.
 *
 * @author Trip.com Group
 */
public abstract class AbstractMessageEngine<T extends MessageWrapper>
        implements StorageEngine, MessageCapable<T> {

    private final CircuitBreaker circuitBreaker;

    protected AbstractMessageEngine(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public OperationResult<?> sendMessage(List<T> wrappers, String commandKey) {
        return circuitBreaker.execute(commandKey,
                () -> doSendMessage(wrappers),
                ex -> {
                    OperationResult<?> result = new OperationResult<>();
                    result.setResultCode(EngineResultCode.ERROR);
                    result.setException(ex instanceof Exception ? (Exception) ex : new RuntimeException(ex));
                    return result;
                });
    }

    protected abstract OperationResult<?> doSendMessage(List<T> wrappers);
}
