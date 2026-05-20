package com.ctrip.garfield.process.compensation;

import com.ctrip.garfield.common.context.CompensationContext;
import com.ctrip.garfield.common.model.CompensationMessage;
import com.ctrip.garfield.common.model.CompensationResult;
import com.ctrip.garfield.common.spi.BackoffStrategy;
import com.ctrip.garfield.common.spi.CompensationExhaustionHandler;
import com.ctrip.garfield.common.spi.MetricsReporter;
import com.ctrip.garfield.common.spi.observation.CompensationObservation;
import com.ctrip.garfield.process.orchestration.WriteOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompensationOrchestratorTest {

    @Mock WriteOrchestrator writeOrchestrator;
    @Mock BackoffStrategy backoffStrategy;
    @Mock CompensationExhaustionHandler exhaustionHandler;
    @Mock MetricsReporter metricsReporter;

    CompensationOrchestrator orchestrator;

    CompensationMessage message;

    @BeforeEach
    void setUp() {
        orchestrator = new CompensationOrchestrator(
                writeOrchestrator, backoffStrategy, exhaustionHandler, metricsReporter);
        message = CompensationMessage.builder()
                .reqClassName("OrderDataUnit")
                .requestData("[{\"orderId\":\"O001\"}]")
                .errorDetails("[]")
                .storageId("redis_follower")
                .traceId("trace-1")
                .build();
    }

    @Test
    void process_noHandler_returnsPermanentFailureAndReportsMetrics() {
        CompensationResult result = orchestrator.process(message, 1);
        assertEquals(CompensationResult.Status.PERMANENT_FAILURE, result.getStatus());
        ArgumentCaptor<CompensationObservation> captor = ArgumentCaptor.forClass(CompensationObservation.class);
        verify(metricsReporter).recordCompensation(captor.capture());
        CompensationObservation obs = captor.getValue();
        assertEquals("NO_HANDLER", obs.getResultStatus());
        assertEquals("OrderDataUnit", obs.getReqClassName());
        assertEquals("redis_follower", obs.getStorageId());
    }

    @SuppressWarnings("unchecked")
    @Test
    void process_handlerReturnsEmptyList_returnsSuccess() throws Exception {
        CompensationHandler<Object> handler = mock(CompensationHandler.class);
        when(handler.getCompensateDataList(any())).thenReturn(Collections.emptyList());

        orchestrator.registerHandler("OrderDataUnit", handler);
        CompensationResult result = orchestrator.process(message, 1);

        assertEquals(CompensationResult.Status.SUCCESS, result.getStatus());
        verify(handler, never()).writeData(any(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void process_writeSucceeds_returnsSuccess() throws Exception {
        CompensationHandler<Object> handler = mock(CompensationHandler.class);
        when(handler.getCompensateDataList(any())).thenReturn(List.of(new Object()));
        when(handler.writeData(any(), eq(writeOrchestrator))).thenReturn(true);

        orchestrator.registerHandler("OrderDataUnit", handler);
        CompensationResult result = orchestrator.process(message, 1);

        assertEquals(CompensationResult.Status.SUCCESS, result.getStatus());
    }

    @SuppressWarnings("unchecked")
    @Test
    void process_writeFails_notExhausted_returnsNeedRetry() throws Exception {
        CompensationHandler<Object> handler = mock(CompensationHandler.class);
        when(handler.getCompensateDataList(any())).thenReturn(List.of(new Object()));
        when(handler.writeData(any(), eq(writeOrchestrator))).thenReturn(false);
        when(backoffStrategy.maxRetries()).thenReturn(16);
        when(backoffStrategy.computeDelay(1)).thenReturn(2000L);

        orchestrator.registerHandler("OrderDataUnit", handler);
        CompensationResult result = orchestrator.process(message, 1);

        assertEquals(CompensationResult.Status.NEED_RETRY, result.getStatus());
        assertEquals(2000, result.getRetryDelayMs());
        assertEquals(1, result.getAttempt());
        assertNull(result.getLastError());
    }

    @SuppressWarnings("unchecked")
    @Test
    void process_writeFails_exhausted_returnsExhaustedWithoutCallingHooks() throws Exception {
        CompensationHandler<Object> handler = mock(CompensationHandler.class);
        when(handler.getCompensateDataList(any())).thenReturn(List.of(new Object()));
        when(handler.writeData(any(), eq(writeOrchestrator))).thenReturn(false);
        when(backoffStrategy.maxRetries()).thenReturn(3);

        orchestrator.registerHandler("OrderDataUnit", handler);
        CompensationResult result = orchestrator.process(message, 3);

        assertEquals(CompensationResult.Status.EXHAUSTED, result.getStatus());
        verify(handler, never()).onExhausted(any(), any());
        verify(exhaustionHandler, never()).onExhausted(any(), anyInt(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void process_handlerThrowsException_triggersRetry() throws Exception {
        CompensationHandler<Object> handler = mock(CompensationHandler.class);
        when(handler.getCompensateDataList(any())).thenThrow(new RuntimeException("parse error"));
        when(backoffStrategy.maxRetries()).thenReturn(16);
        when(backoffStrategy.computeDelay(1)).thenReturn(2000L);

        orchestrator.registerHandler("OrderDataUnit", handler);
        CompensationResult result = orchestrator.process(message, 1);

        assertEquals(CompensationResult.Status.NEED_RETRY, result.getStatus());
        assertEquals(2000, result.getRetryDelayMs());
        assertNotNull(result.getLastError());
        assertEquals("parse error", result.getLastError().getMessage());
    }

    @SuppressWarnings("unchecked")
    @Test
    void process_unlimitedRetries_neverExhausts() throws Exception {
        CompensationHandler<Object> handler = mock(CompensationHandler.class);
        when(handler.getCompensateDataList(any())).thenReturn(List.of(new Object()));
        when(handler.writeData(any(), eq(writeOrchestrator))).thenReturn(false);
        when(backoffStrategy.maxRetries()).thenReturn(-1);
        when(backoffStrategy.computeDelay(anyInt())).thenReturn(1000L);

        orchestrator.registerHandler("OrderDataUnit", handler);
        CompensationResult result = orchestrator.process(message, 999);

        assertEquals(CompensationResult.Status.NEED_RETRY, result.getStatus());
        verify(exhaustionHandler, never()).onExhausted(any(), anyInt(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void process_handlerWithCustomBackoff_usesHandlerStrategy() throws Exception {
        BackoffStrategy customStrategy = mock(BackoffStrategy.class);
        when(customStrategy.maxRetries()).thenReturn(5);
        when(customStrategy.computeDelay(1)).thenReturn(9999L);

        CompensationHandler<Object> handler = mock(CompensationHandler.class);
        when(handler.getCompensateDataList(any())).thenReturn(List.of(new Object()));
        when(handler.writeData(any(), eq(writeOrchestrator))).thenReturn(false);
        when(handler.backoffStrategy()).thenReturn(customStrategy);

        orchestrator.registerHandler("OrderDataUnit", handler);
        CompensationResult result = orchestrator.process(message, 1);

        assertEquals(CompensationResult.Status.NEED_RETRY, result.getStatus());
        assertEquals(9999, result.getRetryDelayMs());
        verify(backoffStrategy, never()).computeDelay(anyInt());
        verify(customStrategy).computeDelay(1);
    }

    @SuppressWarnings("unchecked")
    @Test
    void process_handlerWithNullBackoff_usesGlobalStrategy() throws Exception {
        CompensationHandler<Object> handler = mock(CompensationHandler.class);
        when(handler.getCompensateDataList(any())).thenReturn(List.of(new Object()));
        when(handler.writeData(any(), eq(writeOrchestrator))).thenReturn(false);
        when(handler.backoffStrategy()).thenReturn(null);
        when(backoffStrategy.maxRetries()).thenReturn(16);
        when(backoffStrategy.computeDelay(1)).thenReturn(2000L);

        orchestrator.registerHandler("OrderDataUnit", handler);
        CompensationResult result = orchestrator.process(message, 1);

        assertEquals(CompensationResult.Status.NEED_RETRY, result.getStatus());
        assertEquals(2000, result.getRetryDelayMs());
        verify(backoffStrategy).computeDelay(1);
    }

    @SuppressWarnings("unchecked")
    @Test
    void process_exhausted_doesNotInvokeAnyExhaustionHooks() throws Exception {
        CompensationHandler<Object> handler = mock(CompensationHandler.class);
        when(handler.getCompensateDataList(any())).thenReturn(List.of(new Object()));
        when(handler.writeData(any(), eq(writeOrchestrator))).thenReturn(false);
        when(backoffStrategy.maxRetries()).thenReturn(3);

        orchestrator.registerHandler("OrderDataUnit", handler);
        CompensationResult result = orchestrator.process(message, 3);

        assertEquals(CompensationResult.Status.EXHAUSTED, result.getStatus());
        // Pure function: no side-effect hooks invoked
        verify(handler, never()).onExhausted(any(), any());
        verify(exhaustionHandler, never()).onExhausted(any(), anyInt(), any());
        // Metrics are still reported
        ArgumentCaptor<CompensationObservation> captor = ArgumentCaptor.forClass(CompensationObservation.class);
        verify(metricsReporter).recordCompensation(captor.capture());
        assertEquals("EXHAUSTED", captor.getValue().getResultStatus());
    }

    @SuppressWarnings("unchecked")
    @Test
    void process_exhausted_withException_returnsLastErrorInResult() throws Exception {
        CompensationHandler<Object> handler = mock(CompensationHandler.class);
        RuntimeException boom = new RuntimeException("getCompensateDataList boom");
        when(handler.getCompensateDataList(any())).thenThrow(boom);
        when(backoffStrategy.maxRetries()).thenReturn(3);

        orchestrator.registerHandler("OrderDataUnit", handler);
        CompensationResult result = orchestrator.process(message, 3);

        assertEquals(CompensationResult.Status.EXHAUSTED, result.getStatus());
        assertSame(boom, result.getLastError());
        // No hooks invoked — consumer side handles exhaustion
        verify(handler, never()).onExhausted(any(), any());
        verify(exhaustionHandler, never()).onExhausted(any(), anyInt(), any());
    }
}
