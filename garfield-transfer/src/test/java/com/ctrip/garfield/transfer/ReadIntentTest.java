package com.ctrip.garfield.transfer;

import com.ctrip.garfield.engine.wrapper.KvValueWrapper;
import com.ctrip.garfield.engine.wrapper.QueryRequest;
import com.ctrip.garfield.engine.wrapper.ScanRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReadIntentTest {

    @Test
    void keyLookup_holdsWrappers() {
        List<KvValueWrapper> wrappers = List.of(new KvValueWrapper());
        var intent = new ReadIntent.KeyLookup<>(wrappers);
        assertSame(wrappers, intent.wrappers());
        assertInstanceOf(ReadIntent.KeyLookup.class, intent);
    }

    @Test
    void prefixScan_holdsScanRequest() {
        ScanRequest<KvValueWrapper> req = new ScanRequest<>();
        var intent = new ReadIntent.PrefixScan<>(req);
        assertSame(req, intent.request());
    }

    @Test
    void indexQuery_holdsQueryRequest() {
        QueryRequest<KvValueWrapper> req = new QueryRequest<>();
        var intent = new ReadIntent.IndexQuery<>(req);
        assertSame(req, intent.request());
    }

    @Test
    void patternMatchingSwitch_exhaustive() {
        ReadIntent<KvValueWrapper> intent = new ReadIntent.KeyLookup<>(List.of());
        String type = switch (intent) {
            case ReadIntent.KeyLookup<?> kl -> "KEY";
            case ReadIntent.PrefixScan<?> ps -> "SCAN";
            case ReadIntent.IndexQuery<?> iq -> "QUERY";
        };
        assertEquals("KEY", type);
    }
}
