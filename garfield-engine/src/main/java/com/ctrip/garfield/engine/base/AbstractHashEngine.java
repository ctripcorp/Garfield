package com.ctrip.garfield.engine.base;

import com.ctrip.garfield.common.model.OperationResult;
import com.ctrip.garfield.common.enums.EngineResultCode;
import com.ctrip.garfield.common.spi.CircuitBreaker;
import com.ctrip.garfield.common.spi.StorageEngine;
import com.ctrip.garfield.engine.capability.HashCapable;
import com.ctrip.garfield.engine.wrapper.HashValueWrapper;

import java.util.List;

/**
 * Template base class for hash engines. Wraps every operation with
 * {@link CircuitBreaker} so that subclasses only implement the raw
 * storage calls ({@code doHashGet}, {@code doHashPut}, etc.).
 *
 * @author Trip.com Group
 */
public abstract class AbstractHashEngine<T extends HashValueWrapper>
        implements StorageEngine, HashCapable<T> {

    private final CircuitBreaker circuitBreaker;

    protected AbstractHashEngine(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public OperationResult<T> hashGet(List<T> wrappers, String commandKey) {
        return circuitBreaker.execute(commandKey,
                () -> doHashGet(wrappers),
                ex -> OperationResult.error(EngineResultCode.ERROR,
                        ex instanceof Exception ? (Exception) ex : new RuntimeException(ex)));
    }

    @Override
    public OperationResult<?> hashPut(List<T> wrappers, String commandKey) {
        return circuitBreaker.execute(commandKey,
                () -> doHashPut(wrappers),
                ex -> {
                    OperationResult<?> result = new OperationResult<>();
                    result.setResultCode(EngineResultCode.ERROR);
                    result.setException(ex instanceof Exception ? (Exception) ex : new RuntimeException(ex));
                    return result;
                });
    }

    @Override
    public OperationResult<T> hashGetAll(List<T> wrappers, String commandKey) {
        return circuitBreaker.execute(commandKey,
                () -> doHashGetAll(wrappers),
                ex -> OperationResult.error(EngineResultCode.ERROR,
                        ex instanceof Exception ? (Exception) ex : new RuntimeException(ex)));
    }

    @Override
    public OperationResult<?> hashDelete(List<T> wrappers, String commandKey) {
        return circuitBreaker.execute(commandKey,
                () -> doHashDelete(wrappers),
                ex -> {
                    OperationResult<?> result = new OperationResult<>();
                    result.setResultCode(EngineResultCode.ERROR);
                    result.setException(ex instanceof Exception ? (Exception) ex : new RuntimeException(ex));
                    return result;
                });
    }

    protected abstract OperationResult<T> doHashGet(List<T> wrappers);

    protected abstract OperationResult<?> doHashPut(List<T> wrappers);

    protected abstract OperationResult<T> doHashGetAll(List<T> wrappers);

    protected abstract OperationResult<?> doHashDelete(List<T> wrappers);
}
