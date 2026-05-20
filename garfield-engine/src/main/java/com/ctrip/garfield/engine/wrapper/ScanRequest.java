package com.ctrip.garfield.engine.wrapper;

import lombok.Data;

/**
 * Input object for scan operations. Single-page scan semantics; the caller is
 * responsible for advancing the page.
 *
 * <p>Redis mapping: {@code SCAN MATCH prefix:*} + cursor (prefix is the primary carrier).
 * <p>DynamoDB mapping: {@code Scan} + FilterExpression + LastEvaluatedKey.
 *
 * @param <T> wrapper type for start/end keys
 * @author Trip.com Group
 */
@Data
public class ScanRequest<T> {

    /** Optional prefix. Primary carrier for Redis {@code SCAN MATCH prefix:*} semantics. */
    private String prefix;

    /** Range start (inclusive, optional). */
    private T startKey;

    /** Range end (exclusive, optional). */
    private T endKey;

    /**
     * null = no limit; &gt; 0 = at most N items; &lt;= 0 is invalid.
     * Same semantics as {@link QueryRequest#getLimit()}.
     */
    private Integer limit;

    private String continuationToken;
}
