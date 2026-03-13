package com.netflix.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Generic cache-aside service backed by Redis.
 *
 * How it works for each cached endpoint:
 *   1. Build a cache key (e.g. "netflix:top-directors:10")
 *   2. Check Redis — if the key exists, return cached value (CACHE HIT)
 *   3. If key doesn't exist, call the supplier (which runs the SQL query)
 *   4. Store the SQL result in Redis with a TTL (time to live)
 *   5. Return the result
 *
 * Why this helps:
 *   Under 100 VUs, the first request after TTL expiry queries MySQL.
 *   The next 99 identical requests get instant Redis responses.
 *   MySQL goes from handling hundreds of GROUP BY queries per second
 *   to handling ~1 per minute per endpoint.
 *
 * What if Redis goes down?
 *   The catch blocks log a warning and fall through to MySQL.
 *   The API keeps working — just without caching. This is intentional:
 *   cache failure should degrade performance, not break the service.
 */
@Service
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final long ttlSeconds;

    // Hit/miss counters — exposed via /netflix/cache/stats for the demo
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);

    public CacheService(
            RedisTemplate<String, Object> redisTemplate,
            @Value("${app.cache.ttl-seconds:60}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.ttlSeconds = ttlSeconds;
    }

    /**
     * Get from cache, or compute and store.
     *
     * @param key      Redis key (e.g. "netflix:top-directors:10")
     * @param supplier Function that queries MySQL — only called on cache miss
     * @return Cached or freshly-computed result
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrCompute(String key, Supplier<T> supplier) {
        // Step 1: Try Redis
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                hits.incrementAndGet();
                log.debug("CACHE HIT: {}", key);
                return (T) cached;
            }
        } catch (Exception e) {
            // Redis down? Log it and fall through to MySQL
            log.warn("Redis read failed for key {}: {}", key, e.getMessage());
        }

        // Step 2: Cache miss — run the SQL query
        misses.incrementAndGet();
        log.debug("CACHE MISS: {}", key);
        T result = supplier.get();

        // Step 3: Store in Redis with TTL
        try {
            redisTemplate.opsForValue().set(key, result, ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis write failed for key {}: {}", key, e.getMessage());
        }

        return result;
    }

    // -- Stats for demo/monitoring --

    public long getHits() {
        return hits.get();
    }

    public long getMisses() {
        return misses.get();
    }

    public double getHitRate() {
        long total = hits.get() + misses.get();
        return total == 0 ? 0.0 : (double) hits.get() / total;
    }

    public void resetStats() {
        hits.set(0);
        misses.set(0);
    }
}
