package com.netflix.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * V4 — replaced Redis with an in-process ConcurrentHashMap.
 *
 * Why:
 *   Under sustained load (100 VUs), the 4 Redis-cached endpoints
 *   had P50 = 230-250ms while uncached endpoints had P50 = 1-26ms.
 *   Redis was the bottleneck: Lettuce uses a single shared TCP connection,
 *   so all threads queue on it. Each cache hit costs ~1ms network RTT +
 *   JSON serialization. At 100 concurrent threads, that queue explodes.
 *
 *   A local ConcurrentHashMap lookup takes ~10 nanoseconds (no network,
 *   no serialization). The cached endpoints should now be as fast as
 *   the uncached ones.
 *
 * Trade-off:
 *   Local cache is not shared across multiple app instances.
 *   For horizontal scaling, you'd need a distributed cache (Redis)
 *   with proper connection pooling. For a single instance, local wins.
 *
 * What stays the same:
 *   - Same getOrCompute() method signature
 *   - Same hit/miss counters
 *   - Same TTL behavior
 *   - Controller and Repository are unchanged
 */
@Service
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);

    private record CacheEntry(Object value, long expiresAt) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final long ttlMillis;

    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);

    public CacheService(@Value("${app.cache.ttl-seconds:60}") long ttlSeconds) {
        this.ttlMillis = ttlSeconds * 1000;
    }

    @SuppressWarnings("unchecked")
    public <T> T getOrCompute(String key, Supplier<T> supplier) {
        // Step 1: Check local cache
        CacheEntry entry = cache.get(key);
        if (entry != null && !entry.isExpired()) {
            hits.incrementAndGet();
            return (T) entry.value();
        }

        // Step 2: Cache miss — query MySQL
        misses.incrementAndGet();
        T result = supplier.get();

        // Step 3: Store with TTL
        cache.put(key, new CacheEntry(result, System.currentTimeMillis() + ttlMillis));

        return result;
    }

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
