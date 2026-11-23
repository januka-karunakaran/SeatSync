package com.seatsync.reliability;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, bounded in-memory cache for request idempotency and deduplication.
 * Bounded by maximum capacity and TTL to protect server resources.
 * <p>
 * Keyed by: {@code (userId, requestId, operation)}.
 * Guarantees:
 * <ul>
 *   <li>Retransmissions of the same request ID and payload digest yield the identical cached reply.</li>
 *   <li>Re-using a request ID with a different payload digest is detected and flagged as a conflict.</li>
 * </ul>
 * <p>
 * Owned by Member 01 (Reliability, Events &amp; Asynchronous Messaging).
 */
public class IdempotencyCache {

    public static final int DEFAULT_MAX_ENTRIES = 5000;
    public static final long DEFAULT_TTL_SECONDS = 300; // 5 minutes

    public record CachedOutcome(
        String payloadDigest,
        String serializedReply,
        Instant expiresAt
    ) {
        public boolean isExpired(Instant now) {
            return now.isAfter(expiresAt);
        }
    }

    public enum LookupStatus {
        HIT,
        CONFLICT,
        MISS
    }

    public record LookupResult(
        LookupStatus status,
        String cachedReply
    ) {
        public static LookupResult hit(String reply) {
            return new LookupResult(LookupStatus.HIT, reply);
        }
        public static LookupResult conflict() {
            return new LookupResult(LookupStatus.CONFLICT, null);
        }
        public static LookupResult miss() {
            return new LookupResult(LookupStatus.MISS, null);
        }
    }

    private final Map<String, CachedOutcome> cache = new ConcurrentHashMap<>();
    private final int maxEntries;
    private final long ttlSeconds;
    private final Clock clock;

    public IdempotencyCache() {
        this(DEFAULT_MAX_ENTRIES, DEFAULT_TTL_SECONDS, Clock.systemUTC());
    }

    public IdempotencyCache(int maxEntries, long ttlSeconds, Clock clock) {
        this.maxEntries = maxEntries;
        this.ttlSeconds = ttlSeconds;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Builds a composite cache key.
     */
    public String buildKey(String userId, String requestId, String operation) {
        return (userId != null ? userId : "anon") + ":" + requestId + ":" + operation;
    }

    /**
     * Computes a canonical SHA-256 digest of the request payload string.
     */
    public static String computeDigest(String payload) {
        if (payload == null) {
            return "";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm missing from Java runtime", e);
        }
    }

    /**
     * Looks up an existing outcome for a mutating command.
     */
    public LookupResult lookup(String userId, String requestId, String operation, String payloadDigest) {
        if (requestId == null || operation == null) {
            return LookupResult.miss();
        }

        String key = buildKey(userId, requestId, operation);
        CachedOutcome entry = cache.get(key);
        if (entry == null) {
            return LookupResult.miss();
        }

        Instant now = clock.instant();
        if (entry.isExpired(now)) {
            cache.remove(key);
            return LookupResult.miss();
        }

        if (payloadDigest != null && !payloadDigest.equals(entry.payloadDigest())) {
            return LookupResult.conflict();
        }

        return LookupResult.hit(entry.serializedReply());
    }

    /**
     * Stores a successful or terminal operation outcome in the cache.
     */
    public void record(String userId, String requestId, String operation, String payloadDigest, String serializedReply) {
        if (requestId == null || operation == null || serializedReply == null) {
            return;
        }

        if (cache.size() >= maxEntries) {
            pruneExpired();
            if (cache.size() >= maxEntries) {
                // Remove an arbitrary entry if still exceeding capacity
                var it = cache.keySet().iterator();
                if (it.hasNext()) {
                    it.next();
                    it.remove();
                }
            }
        }

        String key = buildKey(userId, requestId, operation);
        Instant expiresAt = clock.instant().plusSeconds(ttlSeconds);
        cache.put(key, new CachedOutcome(payloadDigest != null ? payloadDigest : "", serializedReply, expiresAt));
    }

    /**
     * Removes all expired entries from the cache.
     */
    public void pruneExpired() {
        Instant now = clock.instant();
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    public int size() {
        return cache.size();
    }

    public void clear() {
        cache.clear();
    }
}
