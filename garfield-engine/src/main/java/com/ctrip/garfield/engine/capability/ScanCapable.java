package com.ctrip.garfield.engine.capability;

import com.ctrip.garfield.common.model.OperationResult;
import com.ctrip.garfield.engine.wrapper.ScanRequest;

/**
 * Engine-layer rare capability — prefix / range scan.
 *
 * <p>Redis pure-KV: {@code SCAN MATCH prefix:*} + cursor, prefix-based.
 * <p>DynamoDB / HBase / ordered stores: startKey/endKey range scan.
 *
 * <p>Single-page semantics; callers pass continuationToken to paginate. A null
 * {@code OperationResult.nextToken} from the engine means all data has been returned.
 *
 * @param <T> start/end key and result wrapper type
 * @author Trip.com Group
 */
public interface ScanCapable<T> {

    OperationResult<T> scan(ScanRequest<T> request, String commandKey);
}
