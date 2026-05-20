package com.ctrip.garfield.engine.base;

import com.ctrip.garfield.common.model.OperationResult;
import com.ctrip.garfield.common.enums.EngineResultCode;
import com.ctrip.garfield.common.spi.CircuitBreaker;
import com.ctrip.garfield.common.spi.StorageEngine;
import com.ctrip.garfield.engine.capability.KvCapable;
import com.ctrip.garfield.engine.wrapper.KvValueWrapper;

import java.util.List;

/**
 * Template base class for KV engines. Wraps every operation with
 * {@link CircuitBreaker} so that subclasses only implement the raw
 * storage calls ({@code doBatchGet}, {@code doBatchPut}, {@code doBatchDelete}).
 *
 * <p>touch / query / scan are rare capabilities not enforced here — subclasses
 * opt in by implementing {@code TouchCapable / QueryCapable / ScanCapable}.
 *
 * @author Trip.com Group
 */
public abstract class AbstractKvEngine<T extends KvValueWrapper>
        implements StorageEngine, KvCapable<T> {

    private final CircuitBreaker circuitBreaker;

    protected AbstractKvEngine(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public OperationResult<T> batchGet(List<T> wrappers, String commandKey) {
        return circuitBreaker.execute(commandKey,
                () -> doBatchGet(wrappers),
                ex -> OperationResult.error(EngineResultCode.ERROR,
                        ex instanceof Exception ? (Exception) ex : new RuntimeException(ex)));
    }

    @Override
    public OperationResult<?> batchPut(List<T> wrappers, String commandKey) {
        return circuitBreaker.execute(commandKey,
                () -> doBatchPut(wrappers),
                ex -> errorResult(ex));
    }

    @Override
    public OperationResult<?> batchDelete(List<T> wrappers, String commandKey) {
        return circuitBreaker.execute(commandKey,
                () -> doBatchDelete(wrappers),
                ex -> errorResult(ex));
    }

    private OperationResult<?> errorResult(Throwable ex) {
        OperationResult<?> result = new OperationResult<>();
        result.setResultCode(EngineResultCode.ERROR);
        result.setException(ex instanceof Exception ? (Exception) ex : new RuntimeException(ex));
        return result;
    }

    protected abstract OperationResult<T> doBatchGet(List<T> wrappers);

    protected abstract OperationResult<?> doBatchPut(List<T> wrappers);

    /**
     * Batch-delete primitive. Must set {@code OperationResult.actualExecuteSize = wrappers.size()}
     * (input count, not actual deletes — see KvCapable#batchDelete Javadoc, Plan B).
     */
    protected abstract OperationResult<?> doBatchDelete(List<T> wrappers);
}
