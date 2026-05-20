package com.ctrip.garfield.common.context;

import com.ctrip.garfield.common.enums.ErrorCode;
import com.ctrip.garfield.common.enums.OperationType;
import com.ctrip.garfield.common.model.BaseDataUnit;
import com.ctrip.garfield.common.model.BaseFailureResult;
import com.ctrip.garfield.common.model.BaseResult;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Request context that flows through the entire read/write pipeline.
 *
 * <p>Callers populate {@code reqClassName} + {@code dataInfos} and pass this
 * context to {@code WriteOrchestrator} or {@code ReadOrchestrator}. The
 * framework fills in {@code result}, {@code errorDetails}, and timing fields.
 *
 * @param <DataInfo>    business data type (e.g. OrderDataUnit)
 * @param <FailureType> per-item failure detail type
 * @author Trip.com Group
 */
@Data
public class GarfieldContext<DataInfo, FailureType extends BaseFailureResult> {
    private List<DataInfo> dataInfos = new ArrayList<>();
    /** Routing key — must match {@link com.ctrip.garfield.common.config.ProcessConfig#getReqClassName()}. */
    private String reqClassName;
    private BaseResult result = new BaseResult();
    private Map<DataInfo, FailureType> errorDetails = new LinkedHashMap<>();
    private String traceId;
    private boolean lockEnabled;
    private String lockerType;
    private OperationType operationType;
    /** If set, reads are directed to this specific engine instead of leader. */
    private String targetEngineId;
    /** Set by WriteOrchestrator after leader write completes; propagated to CompensationMessage and WriteObservation.lagMs. */
    private long writeLeaderEndTimestamp;
    private String rateLimitKey;
    private String continuationToken;
    private Integer limit;
    private boolean allowPartialLockFailure = false;

    public void addErrorDetails(Map<DataInfo, FailureType> details) {
        if (details != null) {
            this.errorDetails.putAll(details);
        }
    }

    public void addErrorDetail(DataInfo data, FailureType detail) {
        if (data != null && detail != null) {
            this.errorDetails.put(data, detail);
        }
    }

    public void setOverallError(ErrorCode errorCode) {
        if (result.isSuccess()) {
            result.setSuccess(false);
            result.setCode(String.valueOf(errorCode.getCode()));
            result.setMessage(errorCode.getMessage());
        }
    }

    public void removeUnlockedItems() {
        if (dataInfos == null) return;
        dataInfos.removeIf(item -> {
            if (item instanceof BaseDataUnit dataUnit && dataUnit.getLockEntity() != null) {
                return !dataUnit.getLockEntity().isLocked();
            }
            return false;
        });
    }
}
