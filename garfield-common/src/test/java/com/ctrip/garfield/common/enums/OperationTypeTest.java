package com.ctrip.garfield.common.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OperationTypeTest {

    @Test
    void shouldHaveSixValues() {
        OperationType[] values = OperationType.values();
        assertEquals(6, values.length);
        assertArrayEquals(
                new OperationType[]{
                        OperationType.BATCH_PUT,
                        OperationType.BATCH_DELETE,
                        OperationType.TOUCH,
                        OperationType.BATCH_GET,
                        OperationType.SCAN,
                        OperationType.QUERY
                },
                values
        );
    }

    @Test
    void tagShouldMatchOrchestratorMethodName() {
        assertEquals("batchPut", OperationType.BATCH_PUT.tag());
        assertEquals("batchDelete", OperationType.BATCH_DELETE.tag());
        assertEquals("touch", OperationType.TOUCH.tag());
        assertEquals("batchGet", OperationType.BATCH_GET.tag());
        assertEquals("scan", OperationType.SCAN.tag());
        assertEquals("query", OperationType.QUERY.tag());
    }

    @Test
    void readOperations_isRead_returnsTrue() {
        assertTrue(OperationType.BATCH_GET.isRead());
        assertTrue(OperationType.SCAN.isRead());
        assertTrue(OperationType.QUERY.isRead());
    }

    @Test
    void writeOperations_isRead_returnsFalse() {
        assertFalse(OperationType.BATCH_PUT.isRead());
        assertFalse(OperationType.BATCH_DELETE.isRead());
        assertFalse(OperationType.TOUCH.isRead());
    }

    @Test
    void isWrite_isAlwaysOppositeOfIsRead() {
        for (OperationType op : OperationType.values()) {
            assertEquals(!op.isRead(), op.isWrite(),
                    op + ".isWrite() should be !isRead()");
        }
    }
}
