package com.eventsphere.backend.reservations.service;

import com.eventsphere.backend.common.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;

/**
 * Thin wrapper around Spring Data Redis for distributed locking.
 *
 * <p>Uses the standard {@code SET key value NX PX ttl} command to acquire a lock
 * and a Lua script for safe, atomic release (only deletes the key when the stored
 * value matches, preventing a late-arriving unlock from releasing another owner's lock).
 *
 * <p>Any {@link RedisConnectionFailureException} is translated to
 * {@link ServiceUnavailableException} so callers receive HTTP 503 when Redis is down.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisLockService {

    private final StringRedisTemplate redisTemplate;

    /** Lua script: delete the key only when the current value equals the caller's token. */
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """,
            Long.class
    );

    /**
     * Attempts to acquire the lock.
     *
     * @param key        Redis key (e.g. {@code "inventory:lock:<categoryId>"})
     * @param value      Unique token used to safely release the lock later
     * @param ttlSeconds Time-to-live for the lock; prevents deadlocks if the JVM crashes
     * @return {@code true} if the lock was acquired, {@code false} if already held
     * @throws ServiceUnavailableException if Redis cannot be reached
     */
    public boolean tryLock(String key, String value, long ttlSeconds) {
        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(key, value, Duration.ofSeconds(ttlSeconds));
            return Boolean.TRUE.equals(acquired);
        } catch (RedisConnectionFailureException ex) {
            throw new ServiceUnavailableException(
                    "Reservation service is temporarily unavailable (Redis unreachable)");
        }
    }

    /**
     * Releases a lock held by this caller.
     * Failures are logged but swallowed — the TTL ensures eventual expiry.
     */
    public void unlock(String key, String value) {
        try {
            redisTemplate.execute(RELEASE_SCRIPT, Collections.singletonList(key), value);
        } catch (RedisConnectionFailureException ex) {
            log.warn("Could not release Redis lock '{}' — it will expire automatically: {}", key, ex.getMessage());
        }
    }
}
