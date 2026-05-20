package com.ctrip.garfield.common.spi.defaults;

import com.ctrip.garfield.common.lock.LockEntity;
import com.ctrip.garfield.common.spi.DistributedLock;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Redisson-based distributed lock using Redis SET NX with token-based ownership.
 * Supports reentrant locking: if the caller already holds the lock (same token),
 * the TTL is refreshed instead of failing.
 *
 * @author Trip.com Group
 */
@RequiredArgsConstructor
public class RedissonDistributedLock implements DistributedLock {

    private final RedissonClient redissonClient;

    /** Atomic compare-and-delete: only deletes if the lock value matches the caller's token. */
    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "return redis.call('del', KEYS[1]) " +
            "else return 0 end";

    /** Reentrant lock: if the token matches, refreshes the TTL instead of acquiring a new lock. */
    private static final String REENTRANT_LOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "redis.call('pexpire', KEYS[1], ARGV[2]) " +
            "return 1 " +
            "else return 0 end";

    private String lockKey(String lockType, String key) {
        return "garfield:lock:" + lockType + ":" + key;
    }

    @Override
    public boolean tryLock(String lockType, LockEntity entity) {
        if (entity.getToken() == null) return false;

        String lockKeyStr = lockKey(lockType, entity.getKey());
        RBucket<String> bucket = redissonClient.getBucket(lockKeyStr);
        boolean acquired = bucket.setIfAbsent(entity.getToken(), Duration.ofMillis(entity.getExpireMs()));
        if (!acquired) {
            Long result = redissonClient.getScript().eval(
                    RScript.Mode.READ_WRITE,
                    REENTRANT_LOCK_SCRIPT,
                    RScript.ReturnType.INTEGER,
                    List.of(lockKeyStr),
                    entity.getToken(), String.valueOf(entity.getExpireMs()));
            return result != null && result == 1L;
        }
        return true;
    }

    @Override
    public boolean checkLock(String lockType, String key, String expectedToken) {
        RBucket<String> bucket = redissonClient.getBucket(lockKey(lockType, key));
        String currentOwner = bucket.get();
        return expectedToken != null && expectedToken.equals(currentOwner);
    }

    @Override
    public List<String> batchGetOwners(String lockType, String... keys) {
        List<String> owners = new ArrayList<>(keys.length);
        for (String key : keys) {
            RBucket<String> bucket = redissonClient.getBucket(lockKey(lockType, key));
            owners.add(bucket.get());
        }
        return owners;
    }

    @Override
    public void unlock(String lockType, LockEntity entity) {
        if (entity.getToken() == null) return;

        String lockKeyStr = lockKey(lockType, entity.getKey());
        redissonClient.getScript().eval(
                RScript.Mode.READ_WRITE,
                UNLOCK_SCRIPT,
                RScript.ReturnType.INTEGER,
                List.of(lockKeyStr),
                entity.getToken());
    }
}
