package com.ctrip.garfield.example.compensation;

import com.ctrip.garfield.common.context.CompensationContext;
import com.ctrip.garfield.common.spi.BackoffStrategy;
import com.ctrip.garfield.common.spi.GarfieldSerializer;
import com.ctrip.garfield.common.spi.defaults.FixedIntervalBackoffStrategy;
import com.ctrip.garfield.example.model.OrderDataUnit;
import com.ctrip.garfield.process.compensation.CompensationHandler;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Example compensation handler for order business data.
 *
 * @author Trip.com Group
 */
@Component
public class OrderCompensationHandler implements CompensationHandler<OrderDataUnit> {

    private final GarfieldSerializer serializer;
    private final BackoffStrategy orderBackoff = new FixedIntervalBackoffStrategy(5000L, 10);

    public OrderCompensationHandler(GarfieldSerializer serializer) {
        this.serializer = serializer;
    }

    @Override
    public String reqClassName() {
        return "OrderDataUnit";
    }

    @Override
    public BackoffStrategy backoffStrategy() {
        return orderBackoff;
    }

    @Override
    public List<OrderDataUnit> getCompensateDataList(CompensationContext<OrderDataUnit> context) {
        return serializer.deserializeList(context.getRequestData(), OrderDataUnit.class);
    }
}
