package com.ctrip.garfield.process.orchestration;

import com.ctrip.garfield.common.context.GarfieldContext;
import com.ctrip.garfield.common.enums.ErrorCode;
import com.ctrip.garfield.common.lock.LockEntity;
import com.ctrip.garfield.common.model.BaseDataUnit;
import com.ctrip.garfield.common.model.BaseFailureResult;
import com.ctrip.garfield.common.spi.DistributedLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Manages distributed lock operations for batch read/write contexts.
 * Supports two modes controlled by {@code GarfieldContext#isAllowPartialLockFailure()}:
 * <ul>
 *   <li><b>All-or-nothing</b> (default): any lock failure aborts the entire batch.</li>
 *   <li><b>Partial</b>: failed items are removed; processing continues with the rest.</li>
 * </ul>
 *
 * @author Trip.com Group
 */
public class LockOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(LockOrchestrator.class);
    private final DistributedLock distributedLock;
    private final ExecutorService lockExecutor;

    public LockOrchestrator(DistributedLock distributedLock, ExecutorService lockExecutor) {
        this.distributedLock = distributedLock;
        this.lockExecutor = lockExecutor;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public boolean batchCheckLocks(GarfieldContext context) {
        if (!context.isLockEnabled()) return true;

        List<BaseDataUnit> dataInfos = (List<BaseDataUnit>) context.getDataInfos();
        String lockType = context.getLockerType();

        List<String> keys = new ArrayList<>();
        List<String> expectedTokens = new ArrayList<>();
        List<BaseDataUnit> lockedDataItems = new ArrayList<>();

        for (BaseDataUnit data : dataInfos) {
            LockEntity le = data.getLockEntity();
            if (le != null && le.getKey() != null) {
                keys.add(le.getKey());
                expectedTokens.add(le.getToken());
                lockedDataItems.add(data);
            }
        }
        if (keys.isEmpty()) return true;

        try {
            List<String> owners = distributedLock.batchGetOwners(lockType, keys.toArray(new String[0]));

            List<BaseDataUnit> failedItems = new ArrayList<>();
            for (int i = 0; i < owners.size(); i++) {
                String owner = owners.get(i);
                String expected = expectedTokens.get(i);
                if (owner != null && !owner.isEmpty() && !owner.equals(expected)) {
                    if (!context.isAllowPartialLockFailure()) {
                        context.setOverallError(ErrorCode.LOCK_CHECK_FAILURE);
                        return false;
                    }
                    failedItems.add(lockedDataItems.get(i));
                    BaseFailureResult failure = lockedDataItems.get(i).createFailureResult("LOCK_CHECK_FAILURE");
                    context.addErrorDetail(lockedDataItems.get(i), failure);
                }
            }

            if (!failedItems.isEmpty()) {
                dataInfos.removeAll(failedItems);
            }
            return !dataInfos.isEmpty();
        } catch (Exception e) {
            log.error("Batch lock check failed", e);
            context.setOverallError(ErrorCode.LOCK_CHECK_FAILURE);
            return false;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public boolean batchGetLocks(GarfieldContext context) {
        if (!context.isLockEnabled()) return true;

        List<BaseDataUnit> dataInfos = (List<BaseDataUnit>) context.getDataInfos();
        String lockType = context.getLockerType();

        List<BaseDataUnit> lockableItems = new ArrayList<>();
        for (BaseDataUnit d : dataInfos) {
            if (d.getLockEntity() != null && d.getLockEntity().getKey() != null) {
                lockableItems.add(d);
            }
        }

        if (lockableItems.isEmpty()) return true;

        // Parallel lock acquisition
        List<CompletableFuture<Void>> futures = lockableItems.stream()
                .map(data -> CompletableFuture.runAsync(() -> {
                    LockEntity le = data.getLockEntity();
                    try {
                        boolean acquired = distributedLock.tryLock(lockType, le);
                        le.setLocked(acquired);
                    } catch (Exception e) {
                        log.error("Lock acquisition failed: key={}", le.getKey(), e);
                        le.setLocked(false);
                    }
                }, lockExecutor))
                .toList();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

        // Classify results
        List<BaseDataUnit> acquired = new ArrayList<>();
        List<BaseDataUnit> failedItems = new ArrayList<>();

        for (BaseDataUnit data : lockableItems) {
            if (data.getLockEntity().isLocked()) {
                acquired.add(data);
            } else {
                failedItems.add(data);
            }
        }

        if (!failedItems.isEmpty()) {
            if (!context.isAllowPartialLockFailure()) {
                // All-or-nothing: release acquired locks and fail
                releaseAcquired(lockType, acquired);
                context.setOverallError(ErrorCode.LOCK_ACQUIRE_FAILURE);
                return false;
            }
            // Partial mode: record failure details then remove failed items
            for (BaseDataUnit data : failedItems) {
                BaseFailureResult failure = data.createFailureResult("LOCK_ACQUIRE_FAILURE");
                context.addErrorDetail(data, failure);
            }
            dataInfos.removeAll(failedItems);
        }

        return !dataInfos.isEmpty();
    }

    @SuppressWarnings("unchecked")
    public void batchReleaseLocks(GarfieldContext context) {
        if (!context.isLockEnabled()) return;

        List<BaseDataUnit> dataInfos = (List<BaseDataUnit>) context.getDataInfos();
        String lockType = context.getLockerType();

        for (BaseDataUnit data : dataInfos) {
            LockEntity lockEntity = data.getLockEntity();
            if (lockEntity == null || !lockEntity.isLocked()) continue;
            try {
                distributedLock.unlock(lockType, lockEntity);
                lockEntity.setLocked(false);
            } catch (Exception e) {
                log.error("Failed to release lock: key={}", lockEntity.getKey(), e);
            }
        }
    }

    private void releaseAcquired(String lockType, List<BaseDataUnit> acquired) {
        for (BaseDataUnit data : acquired) {
            LockEntity le = data.getLockEntity();
            try {
                distributedLock.unlock(lockType, le);
                le.setLocked(false);
            } catch (Exception e) {
                log.error("Failed to release lock during rollback: key={}", le.getKey(), e);
            }
        }
    }
}
