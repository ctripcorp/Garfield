package com.ctrip.garfield.process.orchestration;

import com.ctrip.garfield.common.context.GarfieldContext;
import com.ctrip.garfield.common.enums.ErrorCode;
import com.ctrip.garfield.common.lock.LockEntity;
import com.ctrip.garfield.common.model.BaseDataUnit;
import com.ctrip.garfield.common.model.BaseFailureResult;
import com.ctrip.garfield.common.spi.DistributedLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"rawtypes", "unchecked"})
class LockOrchestratorTest {

    @Mock DistributedLock distributedLock;

    LockOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new LockOrchestrator(distributedLock, Executors.newCachedThreadPool());
    }

    // --- batchCheckLocks ---

    @Test
    void batchCheckLocks_allOrNothing_returnsFalseOnMismatch() {
        GarfieldContext context = createContext(false);
        BaseDataUnit item1 = createDataUnit("key1", "tokenA");
        BaseDataUnit item2 = createDataUnit("key2", "tokenB");
        context.setDataInfos(new ArrayList<>(Arrays.asList(item1, item2)));

        when(distributedLock.batchGetOwners(eq("testLock"), any(String[].class)))
                .thenReturn(Arrays.asList("otherToken", "tokenB"));

        boolean result = orchestrator.batchCheckLocks(context);

        assertFalse(result);
        assertEquals(String.valueOf(ErrorCode.LOCK_CHECK_FAILURE.getCode()),
                context.getResult().getCode());
    }

    @Test
    void batchCheckLocks_partialMode_removesFailedItems() {
        GarfieldContext context = createContext(true);
        BaseDataUnit item1 = createDataUnit("key1", "tokenA");
        BaseDataUnit item2 = createDataUnit("key2", "tokenB");
        List dataInfos = new ArrayList<>(Arrays.asList(item1, item2));
        context.setDataInfos(dataInfos);

        when(distributedLock.batchGetOwners(eq("testLock"), any(String[].class)))
                .thenReturn(Arrays.asList("otherToken", "tokenB"));

        boolean result = orchestrator.batchCheckLocks(context);

        assertTrue(result);
        assertEquals(1, context.getDataInfos().size());
        assertSame(item2, context.getDataInfos().get(0));
    }

    @Test
    void batchCheckLocks_partialMode_returnsFalseWhenAllFail() {
        GarfieldContext context = createContext(true);
        BaseDataUnit item1 = createDataUnit("key1", "tokenA");
        List dataInfos = new ArrayList<>(Arrays.asList(item1));
        context.setDataInfos(dataInfos);

        when(distributedLock.batchGetOwners(eq("testLock"), any(String[].class)))
                .thenReturn(Arrays.asList("otherToken"));

        boolean result = orchestrator.batchCheckLocks(context);

        assertFalse(result);
    }

    // --- batchGetLocks ---

    @Test
    void batchGetLocks_allOrNothing_rollsBackOnPartialFailure() {
        GarfieldContext context = createContext(false);
        BaseDataUnit item1 = createDataUnit("key1", "tokenA");
        BaseDataUnit item2 = createDataUnit("key2", "tokenB");
        context.setDataInfos(new ArrayList<>(Arrays.asList(item1, item2)));

        when(distributedLock.tryLock(eq("testLock"), eq(item1.getLockEntity()))).thenReturn(true);
        when(distributedLock.tryLock(eq("testLock"), eq(item2.getLockEntity()))).thenReturn(false);

        boolean result = orchestrator.batchGetLocks(context);

        assertFalse(result);
        assertEquals(String.valueOf(ErrorCode.LOCK_ACQUIRE_FAILURE.getCode()),
                context.getResult().getCode());
        // Verify rollback: unlock was called for item1
        verify(distributedLock).unlock(eq("testLock"), eq(item1.getLockEntity()));
    }

    @Test
    void batchGetLocks_partialMode_removesFailedKeepsSucceeded() {
        GarfieldContext context = createContext(true);
        BaseDataUnit item1 = createDataUnit("key1", "tokenA");
        BaseDataUnit item2 = createDataUnit("key2", "tokenB");
        List dataInfos = new ArrayList<>(Arrays.asList(item1, item2));
        context.setDataInfos(dataInfos);

        when(distributedLock.tryLock(eq("testLock"), eq(item1.getLockEntity()))).thenReturn(true);
        when(distributedLock.tryLock(eq("testLock"), eq(item2.getLockEntity()))).thenReturn(false);

        boolean result = orchestrator.batchGetLocks(context);

        assertTrue(result);
        assertEquals(1, context.getDataInfos().size());
        assertSame(item1, context.getDataInfos().get(0));
        // No rollback in partial mode
        verify(distributedLock, never()).unlock(any(), any());
    }

    @Test
    void batchGetLocks_allSucceed() {
        GarfieldContext context = createContext(false);
        BaseDataUnit item1 = createDataUnit("key1", "tokenA");
        BaseDataUnit item2 = createDataUnit("key2", "tokenB");
        context.setDataInfos(new ArrayList<>(Arrays.asList(item1, item2)));

        when(distributedLock.tryLock(eq("testLock"), any(LockEntity.class))).thenReturn(true);

        boolean result = orchestrator.batchGetLocks(context);

        assertTrue(result);
        assertEquals(2, context.getDataInfos().size());
    }

    // --- batchReleaseLocks ---

    @Test
    void batchReleaseLocks_releasesHeldLocks() {
        GarfieldContext context = createContext(false);
        BaseDataUnit item1 = createDataUnit("key1", "tokenA");
        item1.getLockEntity().setLocked(true);
        BaseDataUnit item2 = createDataUnit("key2", "tokenB");
        item2.getLockEntity().setLocked(false);
        context.setDataInfos(new ArrayList<>(Arrays.asList(item1, item2)));

        orchestrator.batchReleaseLocks(context);

        verify(distributedLock).unlock(eq("testLock"), eq(item1.getLockEntity()));
        verify(distributedLock, never()).unlock(eq("testLock"), eq(item2.getLockEntity()));
        assertFalse(item1.getLockEntity().isLocked());
    }

    // --- errorDetails partial mode tests ---

    @Test
    void batchCheckLocks_partialMode_populatesErrorDetails() {
        GarfieldContext context = createContext(true);
        BaseDataUnit item1 = createDataUnit("key1", "tokenA");
        BaseDataUnit item2 = createDataUnit("key2", "tokenB");
        List dataInfos = new ArrayList<>(Arrays.asList(item1, item2));
        context.setDataInfos(dataInfos);

        when(distributedLock.batchGetOwners(eq("testLock"), any(String[].class)))
                .thenReturn(Arrays.asList("otherToken", "tokenB"));

        orchestrator.batchCheckLocks(context);

        assertEquals(1, context.getErrorDetails().size());
        assertTrue(context.getErrorDetails().containsKey(item1));
        assertEquals("LOCK_CHECK_FAILURE", ((BaseFailureResult) context.getErrorDetails().get(item1)).getFailureType());
    }

    @Test
    void batchGetLocks_partialMode_populatesErrorDetails() {
        GarfieldContext context = createContext(true);
        BaseDataUnit item1 = createDataUnit("key1", "tokenA");
        BaseDataUnit item2 = createDataUnit("key2", "tokenB");
        List dataInfos = new ArrayList<>(Arrays.asList(item1, item2));
        context.setDataInfos(dataInfos);

        when(distributedLock.tryLock(eq("testLock"), eq(item1.getLockEntity()))).thenReturn(true);
        when(distributedLock.tryLock(eq("testLock"), eq(item2.getLockEntity()))).thenReturn(false);

        orchestrator.batchGetLocks(context);

        assertEquals(1, context.getErrorDetails().size());
        assertTrue(context.getErrorDetails().containsKey(item2));
        assertEquals("LOCK_ACQUIRE_FAILURE", ((BaseFailureResult) context.getErrorDetails().get(item2)).getFailureType());
    }

    @Test
    void batchCheckLocks_partialMode_errorDetailsKeysAreDisjointFromRemainingDataInfos() {
        GarfieldContext context = createContext(true);
        BaseDataUnit item1 = createDataUnit("key1", "tokenA");
        BaseDataUnit item2 = createDataUnit("key2", "tokenB");
        BaseDataUnit item3 = createDataUnit("key3", "tokenC");
        List dataInfos = new ArrayList<>(Arrays.asList(item1, item2, item3));
        context.setDataInfos(dataInfos);

        // item1 and item3 fail lock check, item2 passes
        when(distributedLock.batchGetOwners(eq("testLock"), any(String[].class)))
                .thenReturn(Arrays.asList("otherToken", "tokenB", "otherToken"));

        boolean result = orchestrator.batchCheckLocks(context);

        assertTrue(result);
        // dataInfos should only contain the successful item
        assertEquals(1, context.getDataInfos().size());
        assertSame(item2, context.getDataInfos().get(0));

        // errorDetails should contain the failed items by object reference
        assertEquals(2, context.getErrorDetails().size());
        assertTrue(context.getErrorDetails().containsKey(item1));
        assertTrue(context.getErrorDetails().containsKey(item3));

        // CRITICAL: errorDetails keys must NOT appear in remaining dataInfos
        for (Object failedKey : context.getErrorDetails().keySet()) {
            assertFalse(context.getDataInfos().contains(failedKey),
                    "errorDetails key must not be present in post-mutation dataInfos");
        }
    }

    @Test
    void batchGetLocks_partialMode_errorDetailsKeysAreDisjointFromRemainingDataInfos() {
        GarfieldContext context = createContext(true);
        BaseDataUnit item1 = createDataUnit("key1", "tokenA");
        BaseDataUnit item2 = createDataUnit("key2", "tokenB");
        BaseDataUnit item3 = createDataUnit("key3", "tokenC");
        List dataInfos = new ArrayList<>(Arrays.asList(item1, item2, item3));
        context.setDataInfos(dataInfos);

        // item1 succeeds, item2 and item3 fail
        when(distributedLock.tryLock(eq("testLock"), eq(item1.getLockEntity()))).thenReturn(true);
        when(distributedLock.tryLock(eq("testLock"), eq(item2.getLockEntity()))).thenReturn(false);
        when(distributedLock.tryLock(eq("testLock"), eq(item3.getLockEntity()))).thenReturn(false);

        boolean result = orchestrator.batchGetLocks(context);

        assertTrue(result);
        // dataInfos should only contain the successful item
        assertEquals(1, context.getDataInfos().size());
        assertSame(item1, context.getDataInfos().get(0));

        // errorDetails should contain the failed items by object reference
        assertEquals(2, context.getErrorDetails().size());
        assertTrue(context.getErrorDetails().containsKey(item2));
        assertTrue(context.getErrorDetails().containsKey(item3));

        // CRITICAL: errorDetails keys must NOT appear in remaining dataInfos
        for (Object failedKey : context.getErrorDetails().keySet()) {
            assertFalse(context.getDataInfos().contains(failedKey),
                    "errorDetails key must not be present in post-mutation dataInfos");
        }

        // CRITICAL: remaining dataInfos items must NOT appear in errorDetails
        for (Object remaining : context.getDataInfos()) {
            assertFalse(context.getErrorDetails().containsKey(remaining),
                    "remaining dataInfos item must not appear in errorDetails");
        }
    }

    // --- helpers ---

    private GarfieldContext createContext(boolean allowPartial) {
        GarfieldContext context = new GarfieldContext();
        context.setLockEnabled(true);
        context.setLockerType("testLock");
        context.setAllowPartialLockFailure(allowPartial);
        return context;
    }

    private BaseDataUnit createDataUnit(String key, String token) {
        BaseDataUnit unit = new BaseDataUnit();
        LockEntity le = new LockEntity();
        le.setKey(key);
        le.setToken(token);
        unit.setLockEntity(le);
        return unit;
    }
}
