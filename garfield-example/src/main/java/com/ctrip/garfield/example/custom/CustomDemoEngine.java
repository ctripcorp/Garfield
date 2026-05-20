package com.ctrip.garfield.example.custom;

import com.ctrip.garfield.common.model.OperationResult;
import com.ctrip.garfield.common.spi.CircuitBreaker;
import com.ctrip.garfield.engine.base.AbstractKvEngine;
import com.ctrip.garfield.example.model.OrderKvWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory KV engine for demo purposes, representing a custom proprietary storage medium.
 *
 * <p>Only demonstrates how to integrate an external medium into garfield;
 * minimal implementation, no TTL / concurrency considerations.
 *
 * @author Trip.com Group
 */
public class CustomDemoEngine extends AbstractKvEngine<OrderKvWrapper> {

    private final ConcurrentMap<String, byte[]> store = new ConcurrentHashMap<>();

    public CustomDemoEngine(CircuitBreaker circuitBreaker) {
        super(circuitBreaker);
    }

    @Override
    public String getStorageType() {
        return MyStorageType.MY_CUSTOM_STORAGE.name();
    }

    @Override
    protected OperationResult<?> doBatchPut(List<OrderKvWrapper> wrappers) {
        for (OrderKvWrapper w : wrappers) {
            store.put(w.getKey(), w.getValue());
        }
        return new OperationResult<>();
    }

    @Override
    protected OperationResult<OrderKvWrapper> doBatchGet(List<OrderKvWrapper> wrappers) {
        List<OrderKvWrapper> out = new ArrayList<>();
        for (OrderKvWrapper w : wrappers) {
            byte[] v = store.get(w.getKey());
            if (v != null) {
                OrderKvWrapper hit = new OrderKvWrapper();
                hit.setKey(w.getKey());
                hit.setValue(v);
                out.add(hit);
            }
        }
        return OperationResult.success(out);
    }

    @Override
    protected OperationResult<?> doBatchDelete(List<OrderKvWrapper> wrappers) {
        for (OrderKvWrapper w : wrappers) {
            store.remove(w.getKey());
        }
        OperationResult<?> r = new OperationResult<>();
        r.setActualExecuteSize(wrappers.size());
        return r;
    }
}
