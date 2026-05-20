package com.ctrip.garfield.process.route;

import com.ctrip.garfield.common.model.BaseFailureResult;
import com.ctrip.garfield.process.StorageProcess;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A routing entry for one {@code reqClassName}. Groups the leader and follower
 * storage processes, and provides storageId-based lookup via
 * {@link #getByStorageId} for compensation and targeted reads.
 *
 * <ul>
 *   <li><b>leader</b> — synchronous write, failure stops the pipeline</li>
 *   <li><b>followers</b> — async write, failure triggers compensation</li>
 * </ul>
 *
 * @author Trip.com Group
 */
@Data
public class StorageRoute<ReqData, ResData, FailureType extends BaseFailureResult> {

    private StorageProcess<ReqData, ResData, FailureType> leader;
    private List<StorageProcess<ReqData, ResData, FailureType>> followers = new ArrayList<>();
    /** Flat index of all processes by storageId for O(1) lookup. */
    private Map<String, StorageProcess<ReqData, ResData, FailureType>> processMap;
    private long version;

    public void initProcessMap() {
        processMap = new HashMap<>();
        if (leader != null) {
            String storageId = leader.getStorageEngineInstance().getStorageEngineConfig().getStorageId();
            processMap.put(storageId, leader);
        }
        if (followers != null) {
            for (StorageProcess<ReqData, ResData, FailureType> follower : followers) {
                String storageId = follower.getStorageEngineInstance().getStorageEngineConfig().getStorageId();
                processMap.put(storageId, follower);
            }
        }
    }

    public StorageProcess<ReqData, ResData, FailureType> getByStorageId(String storageId) {
        return processMap != null ? processMap.get(storageId) : null;
    }
}
