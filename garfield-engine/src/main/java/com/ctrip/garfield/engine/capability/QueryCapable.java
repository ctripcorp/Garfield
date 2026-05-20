package com.ctrip.garfield.engine.capability;

import com.ctrip.garfield.common.model.OperationResult;
import com.ctrip.garfield.engine.wrapper.QueryRequest;

/**
 * Engine-layer capability — non-primary-key / secondary-index query.
 *
 * <p>Semantic difference from {@link KvCapable#batchGet}:
 * <ul>
 *   <li>batchGet: input N primary keys, return N rows matched by key</li>
 *   <li>query: input a {@link QueryRequest} (condition + pagination), return all matching rows</li>
 * </ul>
 *
 * <p>Single-shot + paginated (not batch). Engines supporting pagination should set
 * {@code OperationResult.nextToken} as the continuation token; the caller passes
 * it back on the next query call to continue reading.
 *
 * @param <T> query condition / result wrapper type
 * @author Trip.com Group
 */
public interface QueryCapable<T> {

    OperationResult<T> query(QueryRequest<T> request, String commandKey);
}
