package com.ctrip.garfield.common.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompensationResultTest {

    @Test
    void success_hasCorrectStatus() {
        CompensationResult result = CompensationResult.success();
        assertEquals(CompensationResult.Status.SUCCESS, result.getStatus());
        assertNull(result.getLastError());
    }

    @Test
    void needRetry_hasDelayAndAttempt() {
        CompensationResult result = CompensationResult.needRetry(5000, 2, null);
        assertEquals(CompensationResult.Status.NEED_RETRY, result.getStatus());
        assertEquals(5000, result.getRetryDelayMs());
        assertEquals(2, result.getAttempt());
        assertNull(result.getLastError());
    }

    @Test
    void needRetry_carriesLastError() {
        RuntimeException ex = new RuntimeException("transient failure");
        CompensationResult result = CompensationResult.needRetry(3000, 1, ex);
        assertEquals(CompensationResult.Status.NEED_RETRY, result.getStatus());
        assertSame(ex, result.getLastError());
    }

    @Test
    void exhausted_hasAttemptAndError() {
        RuntimeException ex = new RuntimeException("write failed");
        CompensationResult result = CompensationResult.exhausted(16, ex);
        assertEquals(CompensationResult.Status.EXHAUSTED, result.getStatus());
        assertEquals(16, result.getAttempt());
        assertSame(ex, result.getLastError());
    }

    @Test
    void permanentFailure_setsStatusAndMessage() {
        String reason = "No handler for OrderDataUnit";
        CompensationResult result = CompensationResult.permanentFailure(reason);
        assertEquals(CompensationResult.Status.PERMANENT_FAILURE, result.getStatus());
        assertEquals(0, result.getRetryDelayMs());
        assertEquals(0, result.getAttempt());
        assertNotNull(result.getLastError());
        assertEquals(reason, result.getLastError().getMessage());
    }
}
