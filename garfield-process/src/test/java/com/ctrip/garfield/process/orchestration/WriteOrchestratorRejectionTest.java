package com.ctrip.garfield.process.orchestration;

import com.ctrip.garfield.common.config.RetryConfig;
import com.ctrip.garfield.common.config.StorageEngineConfig;
import com.ctrip.garfield.common.context.GarfieldContext;
import com.ctrip.garfield.common.enums.GarfieldStorageType;
import com.ctrip.garfield.common.enums.OperationType;
import com.ctrip.garfield.common.model.BaseDataUnit;
import com.ctrip.garfield.common.model.BaseFailureResult;
import com.ctrip.garfield.common.model.OperationResult;
import com.ctrip.garfield.common.spi.MetricsReporter;
import com.ctrip.garfield.process.StorageProcess;
import com.ctrip.garfield.process.capability.BatchWritable;
import com.ctrip.garfield.process.compensation.CompensationPublisher;
import com.ctrip.garfield.process.route.StorageEngineInstance;
import com.ctrip.garfield.process.route.StorageRoute;
import com.ctrip.garfield.process.route.StorageRouteFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"rawtypes", "unchecked"})
class WriteOrchestratorRejectionTest {

    interface WritableProcessMock extends StorageProcess, BatchWritable {}

    @Mock StorageRouteFactory routeFactory;
    @Mock LockOrchestrator lockOrchestrator;
    @Mock CompensationPublisher compensationPublisher;
    @Mock MetricsReporter metricsReporter;
    @Mock StorageEngineInstance leaderEngineInstance;
    @Mock StorageEngineInstance followerEngineInstance;

    @Test
    void write_followerExecutorRejected_sendsCompensation() {
        WritableProcessMock leaderProcess = mock(WritableProcessMock.class);
        WritableProcessMock followerProcess = mock(WritableProcessMock.class);

        StorageEngineConfig leaderConfig = new StorageEngineConfig();
        leaderConfig.setStorageId("leader_engine");
        leaderConfig.setStorageType(GarfieldStorageType.REDIS);
        when(leaderEngineInstance.getStorageEngineConfig()).thenReturn(leaderConfig);
        when(leaderProcess.getStorageEngineInstance()).thenReturn(leaderEngineInstance);

        StorageEngineConfig followerConfig = new StorageEngineConfig();
        followerConfig.setStorageId("rejected_follower");
        followerConfig.setStorageType(GarfieldStorageType.REDIS);
        followerConfig.setEnabled(true);
        when(followerEngineInstance.getStorageEngineConfig()).thenReturn(followerConfig);
        when(followerProcess.getStorageEngineInstance()).thenReturn(followerEngineInstance);

        StorageRoute route = new StorageRoute();
        route.setLeader(leaderProcess);
        route.setFollowers(List.of(followerProcess));
        route.initProcessMap();
        when(routeFactory.getRoute(any(GarfieldContext.class))).thenReturn(route);
        when(lockOrchestrator.batchCheckLocks(any())).thenReturn(true);

        OperationResult leaderOk = new OperationResult<>();
        when(leaderProcess.write(any())).thenReturn(leaderOk);

        RetryConfig retryConfig = new RetryConfig();
        retryConfig.setMaxAttempts(1);

        // Use a shutdown executor to trigger RejectedExecutionException
        ExecutorService shutdownExecutor = Executors.newSingleThreadExecutor();
        shutdownExecutor.shutdown();

        WriteOrchestrator orchestrator = new WriteOrchestrator(routeFactory, lockOrchestrator,
                compensationPublisher, metricsReporter, retryConfig,
                storageId -> shutdownExecutor, 5000L);

        GarfieldContext<BaseDataUnit, BaseFailureResult> ctx = new GarfieldContext<>();
        ctx.setReqClassName("TestData");
        ctx.setDataInfos(Collections.singletonList(new BaseDataUnit()));

        boolean result = orchestrator.batchPut(ctx);

        assertTrue(result);
        verify(compensationPublisher).sendCompensation(
                any(GarfieldContext.class), isNull(), eq("rejected_follower"),
                eq(OperationType.BATCH_PUT), isNull());
    }
}
