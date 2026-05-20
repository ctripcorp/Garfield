package com.ctrip.garfield.example.transfer;

import com.ctrip.garfield.common.spi.GarfieldSerializer;
import com.ctrip.garfield.example.model.OrderDataUnit;
import com.ctrip.garfield.example.model.OrderMessageWrapper;
import com.ctrip.garfield.transfer.MqTransfer;
import org.springframework.stereotype.Component;

/**
 * One-way conversion from OrderDataUnit to Kafka message wrapper.
 *
 * @author Trip.com Group
 */
@Component("orderMqTransfer")
public class OrderMqTransfer implements MqTransfer<OrderDataUnit, OrderMessageWrapper> {

    private static final String ORDER_EVENTS_TOPIC = "order-events";

    private final GarfieldSerializer serializer;

    public OrderMqTransfer(GarfieldSerializer serializer) {
        this.serializer = serializer;
    }

    @Override
    public OrderMessageWrapper toStorage(OrderDataUnit data) {
        OrderMessageWrapper wrapper = new OrderMessageWrapper();
        wrapper.setTopic(ORDER_EVENTS_TOPIC);
        wrapper.setMessageKey(data.getOrderId());
        wrapper.setMessageBody(serializer.serializeToString(data));
        return wrapper;
    }
}
