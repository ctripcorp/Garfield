package com.ctrip.garfield.transfer;

import com.ctrip.garfield.common.context.GarfieldContext;
import com.ctrip.garfield.engine.wrapper.MessageWrapper;

import java.io.IOException;

/**
 * One-way converter from business DTOs to message engine wrappers.
 * Unlike {@link KvTransfer}, message publishing is write-only.
 *
 * @author Trip.com Group
 */
public interface MqTransfer<ReqData, W extends MessageWrapper> {

    W toStorage(ReqData data) throws IOException;

    default boolean filterBeforePut(GarfieldContext<ReqData, ?> ctx, ReqData data) {
        return true;
    }
}
