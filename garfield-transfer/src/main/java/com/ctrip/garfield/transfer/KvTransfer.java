package com.ctrip.garfield.transfer;

import com.ctrip.garfield.common.context.GarfieldContext;
import com.ctrip.garfield.engine.wrapper.KvValueWrapper;

import java.io.IOException;
import java.util.List;

/**
 * Bidirectional converter between business DTOs and KV engine wrappers.
 *
 * <p>Users implement this for each business data type + storage combination.
 * The framework calls {@code toStorage} before writes and {@code storageToObject}
 * after reads. {@code buildReadIntent} determines the read path (batchGet / scan / query).
 *
 * @param <ReqData> business request data type
 * @param <ResData> business response data type
 * @param <W>       engine-specific wrapper type
 * @author Trip.com Group
 */
public interface KvTransfer<ReqData, ResData, W extends KvValueWrapper> {

    W toStorage(ReqData data) throws IOException;

    List<ResData> storageToObject(W wrapper) throws IOException;

    default ReadIntent<W> buildReadIntent(GarfieldContext<ReqData, ?> ctx) {
        throw new UnsupportedOperationException("This transfer does not support read operations");
    }

    default boolean filterBeforePut(GarfieldContext<ReqData, ?> ctx, ReqData data) {
        return true;
    }
}
