package com.ctrip.garfield.common.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EngineResultCodeTest {

    @Test
    void toErrorCode_success_mapsToSuccess() {
        assertEquals(ErrorCode.SUCCESS, EngineResultCode.SUCCESS.toErrorCode(OperationType.BATCH_PUT));
    }

    @Test
    void toErrorCode_error_writeOp_mapsToWriteLeaderFailure() {
        assertEquals(ErrorCode.WRITE_LEADER_FAILURE, EngineResultCode.ERROR.toErrorCode(OperationType.BATCH_PUT));
        assertEquals(ErrorCode.WRITE_LEADER_FAILURE, EngineResultCode.ERROR.toErrorCode(OperationType.BATCH_DELETE));
        assertEquals(ErrorCode.WRITE_LEADER_FAILURE, EngineResultCode.ERROR.toErrorCode(OperationType.TOUCH));
    }

    @Test
    void toErrorCode_casRollback_mapsToCasConflict() {
        assertEquals(ErrorCode.CAS_CONFLICT, EngineResultCode.CAS_ROLLBACK_ERROR.toErrorCode(OperationType.BATCH_PUT));
    }

    @Test
    void toErrorCode_rateLimit_mapsToRateLimited() {
        assertEquals(ErrorCode.RATE_LIMITED, EngineResultCode.RATE_LIMIT_ERROR.toErrorCode(OperationType.BATCH_PUT));
    }

    @Test
    void toErrorCode_error_readOp_mapsToReadFailure() {
        assertEquals(ErrorCode.READ_FAILURE, EngineResultCode.ERROR.toErrorCode(OperationType.BATCH_GET));
        assertEquals(ErrorCode.READ_FAILURE, EngineResultCode.ERROR.toErrorCode(OperationType.SCAN));
        assertEquals(ErrorCode.READ_FAILURE, EngineResultCode.ERROR.toErrorCode(OperationType.QUERY));
    }

    @Test
    void allResultCodes_allOperationTypes_haveExplicitMapping() {
        for (EngineResultCode code : EngineResultCode.values()) {
            for (OperationType op : OperationType.values()) {
                assertDoesNotThrow(() -> code.toErrorCode(op),
                        "EngineResultCode." + code + ".toErrorCode(" + op + ") should not throw");
            }
        }
    }
}
