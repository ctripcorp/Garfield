package com.ctrip.garfield.transfer;

import com.ctrip.garfield.engine.wrapper.QueryRequest;
import com.ctrip.garfield.engine.wrapper.ScanRequest;

import java.util.List;

/**
 * Sealed interface representing the intent of a read operation.
 * Transfer implementations return the appropriate variant from
 * {@code buildReadIntent()}, allowing the Process layer to dispatch
 * to the correct engine capability (batchGet / scan / query).
 *
 * @param <W> wrapper type used by the engine
 * @author Trip.com Group
 */
public sealed interface ReadIntent<W> {
    record KeyLookup<W>(List<W> wrappers) implements ReadIntent<W> {}
    record PrefixScan<W>(ScanRequest<W> request) implements ReadIntent<W> {}
    record IndexQuery<W>(QueryRequest<W> request) implements ReadIntent<W> {}
}
