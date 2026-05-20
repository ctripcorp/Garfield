package com.ctrip.garfield.common.context;

import com.ctrip.garfield.common.model.CompensationMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompensationContextTest {

    @Test
    void fromMessage_copiesAllFields() {
        CompensationMessage message = CompensationMessage.builder()
                .reqClassName("OrderDataUnit")
                .requestData("{\"orderId\":\"O001\"}")
                .errorDetails("{\"type\":\"ERROR\"}")
                .storageId("redis_follower")
                .traceId("trace-123")
                .build();

        CompensationContext<?> ctx = CompensationContext.fromMessage(message, 3);

        assertEquals("OrderDataUnit", ctx.getReqClassName());
        assertEquals("{\"orderId\":\"O001\"}", ctx.getRequestData());
        assertEquals("{\"type\":\"ERROR\"}", ctx.getErrorDetails());
        assertEquals("redis_follower", ctx.getStorageId());
        assertEquals("trace-123", ctx.getTraceId());
        assertEquals(3, ctx.getAttempt());
        assertNull(ctx.getDataList());
    }
}
