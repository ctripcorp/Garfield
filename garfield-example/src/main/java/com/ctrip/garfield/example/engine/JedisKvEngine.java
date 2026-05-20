package com.ctrip.garfield.example.engine;

import com.ctrip.garfield.common.enums.GarfieldStorageType;
import com.ctrip.garfield.common.model.OperationResult;
import com.ctrip.garfield.common.enums.EngineResultCode;
import com.ctrip.garfield.common.spi.CircuitBreaker;
import com.ctrip.garfield.engine.base.AbstractKvEngine;
import com.ctrip.garfield.engine.capability.TouchCapable;
import com.ctrip.garfield.example.model.OrderKvWrapper;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;

import java.util.ArrayList;
import java.util.List;

/**
 * Jedis-based Redis KV engine with touch support.
 *
 * @author Trip.com Group
 */
public class JedisKvEngine extends AbstractKvEngine<OrderKvWrapper>
        implements TouchCapable<OrderKvWrapper> {

    private final JedisPool jedisPool;

    public JedisKvEngine(JedisPool jedisPool, CircuitBreaker circuitBreaker) {
        super(circuitBreaker);
        this.jedisPool = jedisPool;
    }

    @Override
    public String getStorageType() {
        return GarfieldStorageType.REDIS.name();
    }

    @Override
    protected OperationResult<?> doBatchPut(List<OrderKvWrapper> wrappers) {
        try (Jedis jedis = jedisPool.getResource()) {
            Pipeline pipeline = jedis.pipelined();
            for (OrderKvWrapper wrapper : wrappers) {
                pipeline.set(wrapper.getKey().getBytes(), wrapper.getValue());
            }
            pipeline.sync();
        }
        OperationResult<?> r = new OperationResult<>();
        r.setActualExecuteSize(wrappers.size());
        return r;
    }

    @Override
    protected OperationResult<OrderKvWrapper> doBatchGet(List<OrderKvWrapper> wrappers) {
        try (Jedis jedis = jedisPool.getResource()) {
            Pipeline pipeline = jedis.pipelined();
            List<Response<byte[]>> responses = new ArrayList<>(wrappers.size());
            for (OrderKvWrapper wrapper : wrappers) {
                responses.add(pipeline.get(wrapper.getKey().getBytes()));
            }
            pipeline.sync();
            for (int i = 0; i < wrappers.size(); i++) {
                wrappers.get(i).setValue(responses.get(i).get());
            }
        }
        OperationResult<OrderKvWrapper> result = new OperationResult<>();
        result.setData(wrappers);
        return result;
    }

    @Override
    protected OperationResult<?> doBatchDelete(List<OrderKvWrapper> wrappers) {
        try (Jedis jedis = jedisPool.getResource()) {
            Pipeline pipeline = jedis.pipelined();
            for (OrderKvWrapper wrapper : wrappers) {
                pipeline.del(wrapper.getKey().getBytes());
            }
            pipeline.sync();
        }
        OperationResult<?> r = new OperationResult<>();
        r.setActualExecuteSize(wrappers.size());
        return r;
    }

    @Override
    public OperationResult<?> touch(List<OrderKvWrapper> wrappers, long expireAtMs, String commandKey) {
        try (Jedis jedis = jedisPool.getResource()) {
            for (OrderKvWrapper wrapper : wrappers) {
                jedis.pexpireAt(wrapper.getKey().getBytes(), expireAtMs);
            }
        }
        OperationResult<?> r = new OperationResult<>();
        r.setResultCode(EngineResultCode.SUCCESS);
        r.setActualExecuteSize(wrappers.size());
        return r;
    }
}
