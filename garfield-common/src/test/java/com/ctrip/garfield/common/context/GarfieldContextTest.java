package com.ctrip.garfield.common.context;

import com.ctrip.garfield.common.enums.ErrorCode;
import com.ctrip.garfield.common.lock.LockEntity;
import com.ctrip.garfield.common.model.BaseDataUnit;
import com.ctrip.garfield.common.model.BaseFailureResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GarfieldContextTest {

    @Test
    void newContext_resultDefaultsToSuccess() {
        GarfieldContext<?, ?> ctx = new GarfieldContext<>();
        assertTrue(ctx.getResult().isSuccess());
    }

    @Test
    void setOverallError_setsFirstError() {
        GarfieldContext<Object, BaseFailureResult> ctx = new GarfieldContext<>();
        ctx.setOverallError(ErrorCode.WRITE_LEADER_FAILURE);

        assertFalse(ctx.getResult().isSuccess());
        assertEquals(String.valueOf(ErrorCode.WRITE_LEADER_FAILURE.getCode()), ctx.getResult().getCode());
        assertEquals(ErrorCode.WRITE_LEADER_FAILURE.getMessage(), ctx.getResult().getMessage());
    }

    @Test
    void setOverallError_firstErrorWins() {
        GarfieldContext<Object, BaseFailureResult> ctx = new GarfieldContext<>();
        ctx.setOverallError(ErrorCode.WRITE_LEADER_FAILURE);
        ctx.setOverallError(ErrorCode.LOCK_CHECK_FAILURE);

        assertEquals(String.valueOf(ErrorCode.WRITE_LEADER_FAILURE.getCode()), ctx.getResult().getCode());
    }

    @Test
    void addErrorDetails_accumulatesMapEntries() {
        GarfieldContext<String, BaseFailureResult> ctx = new GarfieldContext<>();
        BaseFailureResult detail1 = new BaseFailureResult("TIMEOUT");
        BaseFailureResult detail2 = new BaseFailureResult("CONFLICT");

        Map<String, BaseFailureResult> batch = new LinkedHashMap<>();
        batch.put("key1", detail1);
        ctx.addErrorDetails(batch);
        ctx.addErrorDetail("key2", detail2);

        assertEquals(2, ctx.getErrorDetails().size());
        assertSame(detail1, ctx.getErrorDetails().get("key1"));
        assertSame(detail2, ctx.getErrorDetails().get("key2"));
    }

    @Test
    void errorDetails_defaultsToEmptyMap() {
        GarfieldContext<String, BaseFailureResult> ctx = new GarfieldContext<>();
        assertNotNull(ctx.getErrorDetails());
        assertTrue(ctx.getErrorDetails().isEmpty());
    }

    @Test
    void removeUnlockedItems_removesItemsWithoutLock() {
        GarfieldContext<BaseDataUnit, BaseFailureResult> ctx = new GarfieldContext<>();

        BaseDataUnit locked = new BaseDataUnit();
        LockEntity le1 = new LockEntity();
        le1.setLocked(true);
        locked.setLockEntity(le1);

        BaseDataUnit notLocked = new BaseDataUnit();
        LockEntity le2 = new LockEntity();
        le2.setLocked(false);
        notLocked.setLockEntity(le2);

        BaseDataUnit noLockEntity = new BaseDataUnit();

        ctx.setDataInfos(new ArrayList<>(List.of(locked, notLocked, noLockEntity)));
        ctx.removeUnlockedItems();

        assertEquals(2, ctx.getDataInfos().size());
        assertTrue(ctx.getDataInfos().contains(locked));
        assertTrue(ctx.getDataInfos().contains(noLockEntity));
    }

    @Test
    void removeUnlockedItems_nullDataInfos_doesNothing() {
        GarfieldContext<BaseDataUnit, BaseFailureResult> ctx = new GarfieldContext<>();
        ctx.setDataInfos(null);
        assertDoesNotThrow(() -> ctx.removeUnlockedItems());
    }
}
