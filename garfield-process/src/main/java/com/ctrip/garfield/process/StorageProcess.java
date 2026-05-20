package com.ctrip.garfield.process;

import com.ctrip.garfield.common.model.BaseFailureResult;
import com.ctrip.garfield.process.route.StorageEngineInstance;

/**
 * Marker interface for all storage process implementations.
 *
 * @author Trip.com Group
 */
public interface StorageProcess<ReqData, ResData, FailureType extends BaseFailureResult> {
    StorageEngineInstance getStorageEngineInstance();
}
