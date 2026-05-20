package com.ctrip.garfield.process.orchestration;

import com.ctrip.garfield.common.context.GarfieldContext;
import com.ctrip.garfield.common.enums.OperationType;
import com.ctrip.garfield.common.exception.GarfieldException;
import com.ctrip.garfield.common.spi.FollowerExecutorProvider;
import com.ctrip.garfield.process.compensation.CompensationPublisher;
import com.ctrip.garfield.process.route.StorageRouteFactory;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class WriteOrchestratorDispatchTest {

    private final FollowerExecutorProvider testProvider = storageId -> Executors.newSingleThreadExecutor();

    private WriteOrchestrator createOrchestrator() {
        return new WriteOrchestrator(
                mock(StorageRouteFactory.class),
                mock(LockOrchestrator.class),
                mock(CompensationPublisher.class),
                null, null, testProvider, 5000L);
    }

    @Test
    void compensateRejectsTouchBecauseItNeedsExpireAtMs() {
        WriteOrchestrator orchestrator = createOrchestrator();

        GarfieldContext<?, ?> ctx = new GarfieldContext<>();
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> orchestrator.compensate(ctx, "engine-1", OperationType.TOUCH));
        assertTrue(ex.getMessage().contains("expireAtMs"));
    }

    @Test
    void compensateRejectsReadOps() {
        WriteOrchestrator orchestrator = createOrchestrator();

        GarfieldContext<?, ?> ctx = new GarfieldContext<>();
        assertThrows(GarfieldException.class,
                () -> orchestrator.compensate(ctx, "engine-1", OperationType.BATCH_GET));
        assertThrows(GarfieldException.class,
                () -> orchestrator.compensate(ctx, "engine-1", OperationType.SCAN));
        assertThrows(GarfieldException.class,
                () -> orchestrator.compensate(ctx, "engine-1", OperationType.QUERY));
    }

    @Test
    void compensateRejectsNullOperationType() {
        WriteOrchestrator orchestrator = createOrchestrator();

        GarfieldContext<?, ?> ctx = new GarfieldContext<>();
        assertThrows(IllegalArgumentException.class,
                () -> orchestrator.compensate(ctx, "engine-1", null));
    }
}
