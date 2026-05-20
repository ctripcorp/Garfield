package com.ctrip.garfield.common.spi;

import com.ctrip.garfield.common.lock.LockEntity;

import java.util.List;

/**
 * SPI for distributed lock operations used by {@code LockOrchestrator}.
 *
 * <p>Locks are namespaced by {@code lockType} and keyed by business key.
 * Token-based ownership enables reentrant locking and safe unlock.
 *
 * @see com.ctrip.garfield.common.spi.defaults.RedissonDistributedLock
 * @author Trip.com Group
 */
public interface DistributedLock {
    boolean tryLock(String lockType, LockEntity entity);
    boolean checkLock(String lockType, String key, String expectedToken);
    List<String> batchGetOwners(String lockType, String... keys);
    void unlock(String lockType, LockEntity entity);
}
