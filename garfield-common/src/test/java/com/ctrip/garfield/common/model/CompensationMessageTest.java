package com.ctrip.garfield.common.model;

import com.ctrip.garfield.common.enums.OperationType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompensationMessageTest {

    @Test
    void shouldCarryOperationTypeAndExpireAtMs() {
        CompensationMessage msg = CompensationMessage.builder()
                .operationType(OperationType.TOUCH)
                .expireAtMs(1700000000000L)
                .build();

        assertEquals(OperationType.TOUCH, msg.getOperationType());
        assertEquals(1700000000000L, msg.getExpireAtMs());
    }

    @Test
    void expireAtMsShouldBeNullableForNonTouch() {
        CompensationMessage msg = CompensationMessage.builder()
                .operationType(OperationType.BATCH_PUT)
                .build();

        assertEquals(OperationType.BATCH_PUT, msg.getOperationType());
        assertNull(msg.getExpireAtMs());
    }
}
