package com.ctrip.garfield.engine.wrapper;

import lombok.Data;

/**
 * Input object for query operations. Single-request + pagination semantics; not a batch.
 *
 * <p>Industry convention (DynamoDB / MongoDB / Bigtable / ES / RediSearch): one query
 * request corresponds to one set of conditions, not a primary-key batch lookup.
 * The framework does not introduce a predicate DSL; condition fields are carried by
 * Wrapper subclasses.
 *
 * @param <T> wrapper type for query conditions and results
 * @author Trip.com Group
 */
@Data
public class QueryRequest<T> {

    /**
     * Query condition. Field semantics are defined by the Wrapper subclass — the
     * framework does not impose a predicate DSL.
     * Populated by {@code KvTransfer.buildQueryRequest} /
     * {@code HashKvTransfer.buildQueryRequest}.
     */
    private T condition;

    /**
     * Maximum number of items to return. Injected by the Process layer from
     * {@code GarfieldContext.getLimit()} before dispatching to the engine.
     * <ul>
     *   <li>null: no limit — the engine scans until data is exhausted and returns
     *       all matches in a single response</li>
     *   <li>&gt; 0: at most N items</li>
     * </ul>
     */
    private Integer limit;

    /**
     * Pagination continuation token. Injected by the Process layer from
     * {@code GarfieldContext.getContinuationToken()}.
     * Pass {@code null} on the first request; pass the
     * {@code OperationResult.nextToken} value from the previous response to continue.
     */
    private String continuationToken;
}
