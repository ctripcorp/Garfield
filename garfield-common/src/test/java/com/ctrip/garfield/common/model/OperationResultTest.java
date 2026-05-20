package com.ctrip.garfield.common.model;

import com.ctrip.garfield.common.enums.EngineResultCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OperationResultTest {

    @Test
    void newResult_defaultsToSuccess() {
        OperationResult<?> result = new OperationResult<>();
        assertTrue(result.isSuccess());
        assertFalse(result.isNeedRetry());
        assertEquals(EngineResultCode.SUCCESS, result.getResultCode());
    }

    @Test
    void isSuccess_falseWhenError() {
        OperationResult<?> result = new OperationResult<>();
        result.setResultCode(EngineResultCode.ERROR);
        assertFalse(result.isSuccess());
    }

    @Test
    void isNeedRetry_trueOnlyForError() {
        OperationResult<?> result = new OperationResult<>();

        result.setResultCode(EngineResultCode.ERROR);
        assertTrue(result.isNeedRetry());

        result.setResultCode(EngineResultCode.CAS_ROLLBACK_ERROR);
        assertFalse(result.isNeedRetry());

        result.setResultCode(EngineResultCode.RATE_LIMIT_ERROR);
        assertFalse(result.isNeedRetry());
    }

    @Test
    void error_staticFactory_createsFailedResult() {
        RuntimeException ex = new RuntimeException("boom");
        OperationResult<?> result = OperationResult.error(EngineResultCode.ERROR, ex);

        assertEquals(EngineResultCode.ERROR, result.getResultCode());
        assertSame(ex, result.getException());
        assertFalse(result.isSuccess());
    }
}
