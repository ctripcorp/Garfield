package com.ctrip.garfield.transfer;

import com.ctrip.garfield.common.context.GarfieldContext;
import com.ctrip.garfield.engine.wrapper.HashValueWrapper;

import java.io.IOException;
import java.util.List;

/**
 * Bidirectional converter between business DTOs and hash engine wrappers.
 * Analogous to {@link KvTransfer} but for hash-based access patterns.
 *
 * @author Trip.com Group
 */
public interface HashKvTransfer<ReqData, ResData, W extends HashValueWrapper> {

    W toStorage(ReqData data) throws IOException;

    List<ResData> storageToObject(W wrapper) throws IOException;

    default ReadIntent<W> buildReadIntent(GarfieldContext<ReqData, ?> ctx) {
        throw new UnsupportedOperationException("This transfer does not support read operations");
    }

    default boolean filterBeforePut(GarfieldContext<ReqData, ?> ctx, ReqData data) {
        return true;
    }
}
