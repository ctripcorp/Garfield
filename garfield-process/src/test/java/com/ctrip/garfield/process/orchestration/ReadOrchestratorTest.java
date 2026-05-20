package com.ctrip.garfield.process.orchestration;

import com.ctrip.garfield.common.config.StorageEngineConfig;
import com.ctrip.garfield.common.context.GarfieldContext;
import com.ctrip.garfield.common.enums.ErrorCode;
import com.ctrip.garfield.common.enums.GarfieldStorageType;
import com.ctrip.garfield.common.exception.GarfieldException;
import com.ctrip.garfield.common.model.BaseDataUnit;
import com.ctrip.garfield.common.model.BaseFailureResult;
import com.ctrip.garfield.common.model.OperationResult;
import com.ctrip.garfield.common.enums.EngineResultCode;
import com.ctrip.garfield.common.spi.MetricsReporter;
import com.ctrip.garfield.process.StorageProcess;
import com.ctrip.garfield.process.capability.BatchReadable;
import com.ctrip.garfield.process.route.StorageEngineInstance;
import com.ctrip.garfield.process.route.StorageRoute;
import com.ctrip.garfield.process.route.StorageRouteFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReadOrchestratorTest {

    @Mock StorageRouteFactory routeFactory;
    @Mock LockOrchestrator lockOrchestrator;
    @Mock MetricsReporter metricsReporter;
    @Mock StorageEngineInstance engineInstance;

    ReadOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new ReadOrchestrator(routeFactory, lockOrchestrator, metricsReporter);
        StorageEngineConfig config = new StorageEngineConfig();
        config.setStorageId("test_engine");
        config.setStorageType(GarfieldStorageType.REDIS);
        lenient().when(engineInstance.getStorageEngineConfig()).thenReturn(config);
    }

    @SuppressWarnings("rawtypes")
    interface ReadableProcessMock extends StorageProcess, BatchReadable {}

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void read_noLeaderProcess_returnsError() {
        StorageRoute route = new StorageRoute();
        when(routeFactory.getRoute(any(GarfieldContext.class))).thenReturn(route);

        GarfieldContext<BaseDataUnit, BaseFailureResult> ctx = new GarfieldContext<>();
        ctx.setReqClassName("TestData");
        ctx.setDataInfos(Collections.emptyList());

        OperationResult<?> result = orchestrator.read(ctx);
        assertFalse(result.isSuccess());
        assertEquals(EngineResultCode.ERROR, result.getResultCode());
        assertNotNull(result.getException());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void read_delegatesToLeader() {
        ReadableProcessMock readableProcess = mock(ReadableProcessMock.class);
        when(readableProcess.getStorageEngineInstance()).thenReturn(engineInstance);
        StorageRoute route = new StorageRoute();
        route.setLeader(readableProcess);
        when(routeFactory.getRoute(any(GarfieldContext.class))).thenReturn(route);
        when(readableProcess.read(any())).thenReturn(OperationResult.success(List.of("data1")));

        GarfieldContext<BaseDataUnit, BaseFailureResult> ctx = new GarfieldContext<>();
        ctx.setReqClassName("TestData");
        ctx.setDataInfos(Collections.emptyList());

        OperationResult<?> result = orchestrator.read(ctx);
        assertTrue(result.isSuccess());
        assertEquals(1, result.getData().size());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void read_nonReadableProcess_returnsUnsupportedError() {
        StorageProcess leaderProcess = mock(StorageProcess.class);
        StorageRoute route = new StorageRoute();
        route.setLeader(leaderProcess);
        when(routeFactory.getRoute(any(GarfieldContext.class))).thenReturn(route);

        GarfieldContext<BaseDataUnit, BaseFailureResult> ctx = new GarfieldContext<>();
        ctx.setReqClassName("TestData");
        ctx.setDataInfos(Collections.emptyList());

        OperationResult<?> result = orchestrator.read(ctx);
        assertFalse(result.isSuccess());
        assertEquals(EngineResultCode.ERROR, result.getResultCode());
        assertNotNull(result.getException());
        assertInstanceOf(GarfieldException.class, result.getException());
        assertEquals(ErrorCode.UNSUPPORTED_OPERATION, ((GarfieldException) result.getException()).getErrorCode());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void read_engineError_propagatesResultCode() {
        ReadableProcessMock readableProcess = mock(ReadableProcessMock.class);
        when(readableProcess.getStorageEngineInstance()).thenReturn(engineInstance);
        StorageRoute route = new StorageRoute();
        route.setLeader(readableProcess);
        when(routeFactory.getRoute(any(GarfieldContext.class))).thenReturn(route);

        RuntimeException engineEx = new RuntimeException("Redis down");
        when(readableProcess.read(any())).thenReturn(OperationResult.error(EngineResultCode.ERROR, engineEx));

        GarfieldContext<BaseDataUnit, BaseFailureResult> ctx = new GarfieldContext<>();
        ctx.setReqClassName("TestData");
        ctx.setDataInfos(Collections.emptyList());

        OperationResult<?> result = orchestrator.read(ctx);
        assertFalse(result.isSuccess());
        assertEquals(EngineResultCode.ERROR, result.getResultCode());
        assertEquals(engineEx, result.getException());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void read_rateLimited_propagatesRateLimitError() {
        ReadableProcessMock readableProcess = mock(ReadableProcessMock.class);
        when(readableProcess.getStorageEngineInstance()).thenReturn(engineInstance);
        StorageRoute route = new StorageRoute();
        route.setLeader(readableProcess);
        when(routeFactory.getRoute(any(GarfieldContext.class))).thenReturn(route);
        when(readableProcess.read(any())).thenReturn(
                OperationResult.error(EngineResultCode.RATE_LIMIT_ERROR, null));

        GarfieldContext<BaseDataUnit, BaseFailureResult> ctx = new GarfieldContext<>();
        ctx.setReqClassName("TestData");
        ctx.setDataInfos(Collections.emptyList());

        OperationResult<?> result = orchestrator.read(ctx);
        assertFalse(result.isSuccess());
        assertEquals(EngineResultCode.RATE_LIMIT_ERROR, result.getResultCode());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void read_lockFailed_returnsError() {
        ReadableProcessMock readableProcess = mock(ReadableProcessMock.class);
        StorageRoute route = new StorageRoute();
        route.setLeader(readableProcess);
        when(routeFactory.getRoute(any(GarfieldContext.class))).thenReturn(route);
        when(lockOrchestrator.batchGetLocks(any())).thenReturn(false);

        GarfieldContext<BaseDataUnit, BaseFailureResult> ctx = new GarfieldContext<>();
        ctx.setReqClassName("TestData");
        ctx.setDataInfos(Collections.emptyList());
        ctx.setLockEnabled(true);

        OperationResult<?> result = orchestrator.read(ctx);
        assertFalse(result.isSuccess());
        assertEquals(EngineResultCode.ERROR, result.getResultCode());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void read_doesNotReleaseLocksAfterSuccessfulRead() {
        ReadableProcessMock readableProcess = mock(ReadableProcessMock.class);
        when(readableProcess.getStorageEngineInstance()).thenReturn(engineInstance);
        StorageRoute route = new StorageRoute();
        route.setLeader(readableProcess);
        when(routeFactory.getRoute(any(GarfieldContext.class))).thenReturn(route);
        when(lockOrchestrator.batchGetLocks(any())).thenReturn(true);
        when(readableProcess.read(any())).thenReturn(OperationResult.success(List.of("data1")));

        GarfieldContext<BaseDataUnit, BaseFailureResult> ctx = new GarfieldContext<>();
        ctx.setReqClassName("TestData");
        ctx.setDataInfos(Collections.emptyList());
        ctx.setLockEnabled(true);

        OperationResult<?> result = orchestrator.read(ctx);

        assertTrue(result.isSuccess());
        assertEquals(EngineResultCode.SUCCESS, result.getResultCode());
        verify(lockOrchestrator).batchGetLocks(ctx);
        verify(lockOrchestrator, never()).batchReleaseLocks(ctx);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void read_emptyData_returnsSuccessWithEmptyList() {
        ReadableProcessMock readableProcess = mock(ReadableProcessMock.class);
        when(readableProcess.getStorageEngineInstance()).thenReturn(engineInstance);
        StorageRoute route = new StorageRoute();
        route.setLeader(readableProcess);
        when(routeFactory.getRoute(any(GarfieldContext.class))).thenReturn(route);
        when(readableProcess.read(any())).thenReturn(
                OperationResult.success(Collections.emptyList()));

        GarfieldContext<BaseDataUnit, BaseFailureResult> ctx = new GarfieldContext<>();
        ctx.setReqClassName("TestData");
        ctx.setDataInfos(Collections.emptyList());

        OperationResult<?> result = orchestrator.read(ctx);
        assertTrue(result.isSuccess());
        assertTrue(result.getData().isEmpty());
    }
}
