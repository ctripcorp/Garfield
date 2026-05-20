package com.ctrip.garfield.example.transfer;

import com.ctrip.garfield.common.context.GarfieldContext;
import com.ctrip.garfield.common.spi.GarfieldSerializer;
import com.ctrip.garfield.example.model.OrderDataUnit;
import com.ctrip.garfield.example.model.OrderKvWrapper;
import com.ctrip.garfield.transfer.KvTransfer;
import com.ctrip.garfield.transfer.ReadIntent;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Bidirectional conversion between OrderDataUnit and Redis KV wrapper.
 *
 * @author Trip.com Group
 */
@Component("orderKvTransfer")
public class OrderKvTransfer implements KvTransfer<OrderDataUnit, OrderDataUnit, OrderKvWrapper> {

    private final GarfieldSerializer serializer;

    public OrderKvTransfer(GarfieldSerializer serializer) {
        this.serializer = serializer;
    }

    @Override
    public OrderKvWrapper toStorage(OrderDataUnit data) {
        OrderKvWrapper wrapper = new OrderKvWrapper();
        wrapper.setKey(data.getOrderId());
        wrapper.setValue(serializer.serialize(data));
        return wrapper;
    }

    @Override
    public List<OrderDataUnit> storageToObject(OrderKvWrapper wrapper) {
        return Collections.singletonList(serializer.deserialize(wrapper.getValue(), OrderDataUnit.class));
    }

    @Override
    public ReadIntent<OrderKvWrapper> buildReadIntent(GarfieldContext<OrderDataUnit, ?> ctx) {
        List<OrderKvWrapper> wrappers = ctx.getDataInfos().stream()
                .map(this::toStorage)
                .toList();
        return new ReadIntent.KeyLookup<>(wrappers);
    }
}
