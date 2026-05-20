package com.ctrip.garfield.process.orchestration;

import com.ctrip.garfield.common.config.RetryConfig;
import com.ctrip.garfield.common.config.StorageEngineConfig;
import com.ctrip.garfield.common.context.GarfieldContext;
import com.ctrip.garfield.common.enums.GarfieldStorageType;
import com.ctrip.garfield.common.enums.OperationType;
import com.ctrip.garfield.common.model.BaseDataUnit;
import com.ctrip.garfield.common.model.BaseFailureResult;
import com.ctrip.garfield.common.model.OperationResult;
import com.ctrip.garfield.common.enums.EngineResultCode;
import com.ctrip.garfield.common.spi.MetricsReporter;
import com.ctrip.garfield.common.spi.observation.WriteObservation;
import com.ctrip.garfield.process.StorageProcess;
import com.ctrip.garfield.process.capability.BatchWritable;
import com.ctrip.garfield.process.compensation.CompensationPublisher;
import com.ctrip.garfield.process.route.StorageEngineInstance;
import com.ctrip.garfield.process.route.StorageRoute;
import com.ctrip.garfield.process.route.StorageRouteFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ctrip.garfield.common.enums.ErrorCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WriteOrchestratorTest {

    /** Combined mock type: StorageProcess + BatchWritable. */
    @SuppressWarnings("rawtypes")
    interface WritableProcessMock extends StorageProcess, BatchWritable {}

    @Mock StorageRouteFactory routeFactory;
    @Mock LockOrchestrator lockOrchestrator;
    @Mock CompensationPublisher compensationPublisher;
    @Mock MetricsReporter metricsReporter;
    @Mock StorageEngineInstance engineInstance;
    @Mock StorageEngineInstance followerEngineInstance;

    WritableProcessMock leaderProcess;
    WritableProcessMock followerProcess;

    WriteOrchestrator orchestrator;
    StorageRoute route;

    @BeforeEach
    void setUp() {
        leaderProcess = mock(WritableProcessMock.class);
        followerProcess = mock(WritableProcessMock.class);

        RetryConfig retryConfig = new RetryConfig();
        retryConfig.setMaxAttempts(1);
        orchestrator = new WriteOrchestrator(routeFactory, lockOrchestrator,
                compensationPublisher, metricsReporter, retryConfig,
                storageId -> Executors.newSingleThreadExecutor(), 5000L);
        route = new StorageRoute();

        StorageEngineConfig engineConfig = new StorageEngineConfig();
        engineConfig.setStorageId("test_engine");
        engineConfig.setStorageType(GarfieldStorageType.REDIS);
        lenient().when(engineInstance.getStorageEngineConfig()).thenReturn(engineConfig);
        lenient().when(leaderProcess.getStorageEngineInstance()).thenReturn(engineInstance);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void write_leaderSucceeds_returnsTrue() {
        route.setLeader(leaderProcess);
        route.initProcessMap();
        when(routeFactory.getRoute(any(GarfieldContext.class))).thenReturn(route);
        when(lockOrchestrator.batchCheckLocks(any())).thenReturn(true);

        OperationResult successResult = new OperationResult<>();
        when(leaderProcess.write(any())).thenReturn(successResult);

        GarfieldContext<BaseDataUnit, BaseFailureResult> ctx = new GarfieldContext<>();
        ctx.setReqClassName("TestData");
        ctx.setDataInfos(Collections.singletonList(new BaseDataUnit()));

        boolean result = orchestrator.batchPut(ctx);
        assertTrue(result);
        assertTrue(ctx.getResult().isSuccess());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void write_leaderFails_returnsFalse() {
        route.setLeader(leaderProcess);
        route.initProcessMap();
        when(routeFactory.getRoute(any(GarfieldContext.class))).thenReturn(route);
        when(lockOrchestrator.batchCheckLocks(any())).thenReturn(true);

        OperationResult failResult = new OperationResult<>();
        failResult.setResultCode(EngineResultCode.ERROR);
        when(leaderProcess.write(any())).thenReturn(failResult);

        GarfieldContext<BaseDataUnit, BaseFailureResult> ctx = new GarfieldContext<>();
        ctx.setReqClassName("TestData");
        ctx.setDataInfos(Collections.singletonList(new BaseDataUnit()));

        boolean result = orchestrator.batchPut(ctx);
        assertFalse(result);
        assertFalse(ctx.getResult().isSuccess());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void write_followerWriteSucceeds_recordsFollowerWriteObservation() {
        StorageEngineConfig followerConfig = new StorageEngineConfig();
        followerConfig.setStorageId("follower_engine");
        followerConfig.setStorageType(GarfieldStorageType.REDIS);
        followerConfig.setEnabled(true);
        lenient().when(followerEngineInstance.getStorageEngineConfig()).thenReturn(followerConfig);
        lenient().when(followerProcess.getStorageEngineInstance()).thenReturn(followerEngineInstance);

        route.setLeader(leaderProcess);
        route.setFollowers(List.of(followerProcess));
        route.initProcessMap();
        when(routeFactory.getRoute(any(GarfieldContext.class))).thenReturn(route);
        when(lockOrchestrator.batchCheckLocks(any())).thenReturn(true);

        OperationResult leaderOk = new OperationResult<>();
        when(leaderProcess.write(any())).thenReturn(leaderOk);
        OperationResult followerOk = new OperationResult<>();
        when(followerProcess.write(any())).thenReturn(followerOk);

        GarfieldContext<BaseDataUnit, BaseFailureResult> ctx = new GarfieldContext<>();
        ctx.setReqClassName("TestData");
        ctx.setDataInfos(Collections.singletonList(new BaseDataUnit()));

        orchestrator.batchPut(ctx);

        ArgumentCaptor<WriteObservation> captor = ArgumentCaptor.forClass(WriteObservation.class);
        verify(metricsReporter, timeout(1000).atLeast(2)).recordWrite(captor.capture());

        WriteObservation followerObs = captor.getAllValues().stream()
                .filter(o -> !o.isLeader()).findFirst().orElseThrow();
        assertEquals("follower_engine", followerObs.getEngineId());
        assertEquals(GarfieldStorageType.REDIS, followerObs.getStorageType());
        assertFalse(followerObs.isLeader());
        assertNotNull(followerObs.getLagMs());
        assertTrue(followerObs.getLagMs() >= 0);
        verify(compensationPublisher, never()).sendCompensation(any(), any(), any());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void write_followerWriteFails_recordsFollowerFailureAndSendsCompensation() {
        StorageEngineConfig followerConfig = new StorageEngineConfig();
        followerConfig.setStorageId("follower_engine");
        followerConfig.setStorageType(GarfieldStorageType.REDIS);
        followerConfig.setEnabled(true);
        lenient().when(followerEngineInstance.getStorageEngineConfig()).thenReturn(followerConfig);
        lenient().when(followerProcess.getStorageEngineInstance()).thenReturn(followerEngineInstance);

        route.setLeader(leaderProcess);
        route.setFollowers(List.of(followerProcess));
        route.initProcessMap();
        when(routeFactory.getRoute(any(GarfieldContext.class))).thenReturn(route);
        when(lockOrchestrator.batchCheckLocks(any())).thenReturn(true);

        OperationResult leaderOk = new OperationResult<>();
        when(leaderProcess.write(any())).thenReturn(leaderOk);
        OperationResult followerFail = new OperationResult<>();
        followerFail.setResultCode(EngineResultCode.ERROR);
        when(followerProcess.write(any())).thenReturn(followerFail);

        GarfieldContext<BaseDataUnit, BaseFailureResult> ctx = new GarfieldContext<>();
        ctx.setReqClassName("TestData");
        ctx.setDataInfos(Collections.singletonList(new BaseDataUnit()));

        orchestrator.batchPut(ctx);

        ArgumentCaptor<WriteObservation> captor = ArgumentCaptor.forClass(WriteObservation.class);
        verify(metricsReporter, timeout(1000).atLeast(2)).recordWrite(captor.capture());

        WriteObservation followerObs = captor.getAllValues().stream()
                .filter(o -> !o.isLeader()).findFirst().orElseThrow();
        assertNull(followerObs.getLagMs());
        assertEquals(EngineResultCode.ERROR, followerObs.getOperationResult().getResultCode());

        verify(compensationPublisher, timeout(1000).times(1))
                .sendCompensation(any(GarfieldContext.class), eq(followerFail), eq("follower_engine"),
                        eq(OperationType.BATCH_PUT), isNull());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void write_lockCheckFails_skipsLeaderWrite() {
        route.setLeader(leaderProcess);
        route.initProcessMap();
        when(routeFactory.getRoute(any(GarfieldContext.class))).thenReturn(route);
        doAnswer(invocation -> {
            GarfieldContext c = invocation.getArgument(0);
            c.setOverallError(ErrorCode.LOCK_CHECK_FAILURE);
            return false;
        }).when(lockOrchestrator).batchCheckLocks(any());

        GarfieldContext<BaseDataUnit, BaseFailureResult> ctx = new GarfieldContext<>();
        ctx.setReqClassName("TestData");
        ctx.setDataInfos(Collections.singletonList(new BaseDataUnit()));

        boolean result = orchestrator.batchPut(ctx);
        assertFalse(result);
        assertFalse(ctx.getResult().isSuccess());
        verify(leaderProcess, never()).write(any());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void write_leaderPartialFailure_returnsTrue() {
        route.setLeader(leaderProcess);
        route.initProcessMap();
        when(routeFactory.getRoute(any(GarfieldContext.class))).thenReturn(route);
        when(lockOrchestrator.batchCheckLocks(any())).thenReturn(true);

        BaseDataUnit item0 = new BaseDataUnit();
        BaseDataUnit item1 = new BaseDataUnit();
        BaseDataUnit item2 = new BaseDataUnit();

        OperationResult leaderResult = new OperationResult<>();
        leaderResult.setResultCode(EngineResultCode.SUCCESS);
        BaseFailureResult failure = new BaseFailureResult("TIMEOUT");
        failure.setDataIndex(1);
        leaderResult.setErrorDetails(List.of(failure));
        when(leaderProcess.write(any())).thenReturn(leaderResult);

        GarfieldContext<BaseDataUnit, BaseFailureResult> ctx = new GarfieldContext<>();
        ctx.setReqClassName("TestData");
        ctx.setDataInfos(new ArrayList<>(List.of(item0, item1, item2)));

        boolean result = orchestrator.batchPut(ctx);
        assertTrue(result);
        assertTrue(ctx.getResult().isSuccess());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void write_leaderPartialFailure_populatesErrorDetailsMap() {
        route.setLeader(leaderProcess);
        route.initProcessMap();
        when(routeFactory.getRoute(any(GarfieldContext.class))).thenReturn(route);
        when(lockOrchestrator.batchCheckLocks(any())).thenReturn(true);

        BaseDataUnit item0 = new BaseDataUnit();
        BaseDataUnit item1 = new BaseDataUnit();
        BaseDataUnit item2 = new BaseDataUnit();

        OperationResult leaderResult = new OperationResult<>();
        leaderResult.setResultCode(EngineResultCode.SUCCESS);
        BaseFailureResult failure = new BaseFailureResult("TIMEOUT");
        failure.setDataIndex(1);
        leaderResult.setErrorDetails(List.of(failure));
        when(leaderProcess.write(any())).thenReturn(leaderResult);

        GarfieldContext<BaseDataUnit, BaseFailureResult> ctx = new GarfieldContext<>();
        ctx.setReqClassName("TestData");
        ctx.setDataInfos(new ArrayList<>(List.of(item0, item1, item2)));

        orchestrator.batchPut(ctx);

        assertEquals(1, ctx.getErrorDetails().size());
        assertTrue(ctx.getErrorDetails().containsKey(item1));
        assertEquals("TIMEOUT", ctx.getErrorDetails().get(item1).getFailureType());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void write_leaderPartialFailure_trimsDataInfos() {
        route.setLeader(leaderProcess);
        route.initProcessMap();
        when(routeFactory.getRoute(any(GarfieldContext.class))).thenReturn(route);
        when(lockOrchestrator.batchCheckLocks(any())).thenReturn(true);

        BaseDataUnit item0 = new BaseDataUnit();
        BaseDataUnit item1 = new BaseDataUnit();
        BaseDataUnit item2 = new BaseDataUnit();

        OperationResult leaderResult = new OperationResult<>();
        leaderResult.setResultCode(EngineResultCode.SUCCESS);
        BaseFailureResult failure = new BaseFailureResult("TIMEOUT");
        failure.setDataIndex(1);
        leaderResult.setErrorDetails(List.of(failure));
        when(leaderProcess.write(any())).thenReturn(leaderResult);

        GarfieldContext<BaseDataUnit, BaseFailureResult> ctx = new GarfieldContext<>();
        ctx.setReqClassName("TestData");
        ctx.setDataInfos(new ArrayList<>(List.of(item0, item1, item2)));

        orchestrator.batchPut(ctx);

        assertEquals(2, ctx.getDataInfos().size());
        assertSame(item0, ctx.getDataInfos().get(0));
        assertSame(item2, ctx.getDataInfos().get(1));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void write_leaderPartialFailure_recordsWriteObservationWithErrorDetails() {
        route.setLeader(leaderProcess);
        route.initProcessMap();
        when(routeFactory.getRoute(any(GarfieldContext.class))).thenReturn(route);
        when(lockOrchestrator.batchCheckLocks(any())).thenReturn(true);

        BaseDataUnit item0 = new BaseDataUnit();
        BaseDataUnit item1 = new BaseDataUnit();

        OperationResult leaderResult = new OperationResult<>();
        leaderResult.setResultCode(EngineResultCode.SUCCESS);
        BaseFailureResult failure = new BaseFailureResult("CONFLICT");
        failure.setDataIndex(0);
        leaderResult.setErrorDetails(List.of(failure));
        when(leaderProcess.write(any())).thenReturn(leaderResult);

        GarfieldContext<BaseDataUnit, BaseFailureResult> ctx = new GarfieldContext<>();
        ctx.setReqClassName("TestData");
        ctx.setDataInfos(new ArrayList<>(List.of(item0, item1)));

        orchestrator.batchPut(ctx);

        ArgumentCaptor<WriteObservation> captor = ArgumentCaptor.forClass(WriteObservation.class);
        verify(metricsReporter).recordWrite(captor.capture());

        WriteObservation obs = captor.getValue();
        assertTrue(obs.isLeader());
        assertEquals("TestData", obs.getReqClassName());
        assertEquals(2, obs.getTotalCount());
        assertEquals(EngineResultCode.SUCCESS, obs.getOperationResult().getResultCode());
        assertNotNull(obs.getOperationResult().getErrorDetails());
        assertEquals(1, obs.getOperationResult().getErrorDetails().size());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void write_followerTimeout_sendsCompensationOnlyOnce() throws Exception {
        StorageEngineConfig followerConfig = new StorageEngineConfig();
        followerConfig.setStorageId("slow_follower");
        followerConfig.setStorageType(GarfieldStorageType.REDIS);
        followerConfig.setEnabled(true);
        lenient().when(followerEngineInstance.getStorageEngineConfig()).thenReturn(followerConfig);
        lenient().when(followerProcess.getStorageEngineInstance()).thenReturn(followerEngineInstance);

        route.setLeader(leaderProcess);
        route.setFollowers(List.of(followerProcess));
        route.initProcessMap();
        when(routeFactory.getRoute(any(GarfieldContext.class))).thenReturn(route);
        when(lockOrchestrator.batchCheckLocks(any())).thenReturn(true);

        OperationResult leaderOk = new OperationResult<>();
        when(leaderProcess.write(any())).thenReturn(leaderOk);

        // Follower blocks for 200ms then returns success — but timeout is 50ms
        java.util.concurrent.CountDownLatch invoked = new java.util.concurrent.CountDownLatch(1);
        OperationResult followerOk = new OperationResult<>();
        when(followerProcess.write(any())).thenAnswer(inv -> {
            invoked.countDown();
            Thread.sleep(200);
            return followerOk;
        });

        // Short timeout orchestrator (50ms)
        RetryConfig retryConfig = new RetryConfig();
        retryConfig.setMaxAttempts(1);
        WriteOrchestrator shortTimeoutOrch = new WriteOrchestrator(routeFactory, lockOrchestrator,
                compensationPublisher, metricsReporter, retryConfig,
                storageId -> java.util.concurrent.Executors.newSingleThreadExecutor(), 50L);

        GarfieldContext<BaseDataUnit, BaseFailureResult> ctx = new GarfieldContext<>();
        ctx.setReqClassName("TestData");
        ctx.setDataInfos(Collections.singletonList(new BaseDataUnit()));

        shortTimeoutOrch.batchPut(ctx);

        // Wait for follower thread to finish (it will complete after 200ms)
        invoked.await(1, TimeUnit.SECONDS);
        Thread.sleep(300);

        // Compensation should be sent exactly once (by timeout path), NOT twice
        verify(compensationPublisher, timeout(500).times(1))
                .sendCompensation(any(), any(), eq("slow_follower"), eq(OperationType.BATCH_PUT), isNull());
        // Metrics should also be reported exactly once for the follower
        ArgumentCaptor<WriteObservation> captor2 = ArgumentCaptor.forClass(WriteObservation.class);
        verify(metricsReporter, atLeast(1)).recordWrite(captor2.capture());
        long followerReports = captor2.getAllValues().stream().filter(o -> !o.isLeader()).count();
        assertEquals(1, followerReports, "Follower write should be reported exactly once");
    }
}
