package com.ctrip.garfield.common.model;

import com.ctrip.garfield.common.lock.LockEntity;
import lombok.Data;

/**
 * Base class for all business data units flowing through the framework.
 * Subclasses should override {@link #createFailureResult} to produce
 * domain-specific failure details for partial-failure scenarios.
 *
 * @author Trip.com Group
 */
@Data
public class BaseDataUnit {
    private LockEntity lockEntity;
    /** CAS version for optimistic locking; set before write, compared by the engine. */
    private Long newDataVersion;

    /**
     * Factory method for creating per-item failure results. Default returns a
     * plain {@link BaseFailureResult} carrying only the {@code failureType};
     * override when your domain needs richer failure fields (e.g. conflicting
     * version, current owner of a lock).
     */
    public BaseFailureResult createFailureResult(String failureType) {
        return new BaseFailureResult(failureType);
    }
}
