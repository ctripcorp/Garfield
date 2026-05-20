package com.ctrip.garfield.common.model;

import lombok.Getter;

/**
 * Outcome of a single compensation attempt, returned by
 * {@code CompensationOrchestrator.process()}. Always returned (never throws),
 * so the caller can inspect the result and decide how to ACK or re-deliver.
 *
 * @author Trip.com Group
 */
@Getter
public class CompensationResult {

    public enum Status {
        SUCCESS,
        NEED_RETRY,
        EXHAUSTED,
        PERMANENT_FAILURE
    }

    private final Status status;
    private final long retryDelayMs;
    private final int attempt;
    private final Exception lastError;

    private CompensationResult(Status status, long retryDelayMs, int attempt, Exception lastError) {
        this.status = status;
        this.retryDelayMs = retryDelayMs;
        this.attempt = attempt;
        this.lastError = lastError;
    }

    public static CompensationResult success() {
        return new CompensationResult(Status.SUCCESS, 0, 0, null);
    }

    public static CompensationResult needRetry(long delayMs, int attempt, Exception lastError) {
        return new CompensationResult(Status.NEED_RETRY, delayMs, attempt, lastError);
    }

    public static CompensationResult exhausted(int attempt, Exception lastError) {
        return new CompensationResult(Status.EXHAUSTED, 0, attempt, lastError);
    }

    public static CompensationResult permanentFailure(String reason) {
        return new CompensationResult(Status.PERMANENT_FAILURE, 0, 0,
                new IllegalStateException(reason));
    }
}
