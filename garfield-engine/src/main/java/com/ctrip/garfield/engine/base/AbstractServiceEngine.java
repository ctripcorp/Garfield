package com.ctrip.garfield.engine.base;

import com.ctrip.garfield.common.model.OperationResult;
import com.ctrip.garfield.common.enums.EngineResultCode;
import com.ctrip.garfield.common.spi.CircuitBreaker;
import com.ctrip.garfield.common.spi.StorageEngine;
import com.ctrip.garfield.engine.capability.ServiceCallCapable;

/**
 * Template base class for service call engines. Wraps {@code invoke} with
 * {@link CircuitBreaker} so that subclasses only implement {@code doInvoke}.
 *
 * @param <T> request wrapper type
 * @author Trip.com Group
 */
public abstract class AbstractServiceEngine<T>
        implements StorageEngine, ServiceCallCapable<T> {

    private final CircuitBreaker circuitBreaker;

    protected AbstractServiceEngine(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public OperationResult<?> invoke(T requestWrapper, String commandKey) {
        return circuitBreaker.execute(commandKey,
                () -> doInvoke(requestWrapper),
                ex -> {
                    OperationResult<?> result = new OperationResult<>();
                    result.setResultCode(EngineResultCode.ERROR);
                    result.setException(ex instanceof Exception ? (Exception) ex : new RuntimeException(ex));
                    return result;
                });
    }

    protected abstract OperationResult<?> doInvoke(T requestWrapper);
}
