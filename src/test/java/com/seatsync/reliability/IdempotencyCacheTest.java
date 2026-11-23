package com.seatsync.reliability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("IdempotencyCache Unit Tests (U07)")
class IdempotencyCacheTest {

    private static class SettableClock extends Clock {
        private Instant now = Instant.parse("2026-11-23T12:00:00Z");
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advanceSeconds(long s) { now = now.plusSeconds(s); }
    }

    private SettableClock clock;
    private IdempotencyCache cache;

    @BeforeEach
    void setUp() {
        clock = new SettableClock();
        cache = new IdempotencyCache(100, 60, clock); // 60s TTL
    }

    @Test
    @DisplayName("Returns HIT with original cached reply for identical request")
    void testIdenticalRequestReturnsHit() {
        String payload = "{\"type\":\"BOOK_SEAT\",\"showId\":\"SHOW-01\",\"seatId\":\"A1\"}";
        String digest = IdempotencyCache.computeDigest(payload);
        String reply = "{\"type\":\"BOOK_OK\",\"bookingId\":\"BKG-100\"}";

        cache.record("user-1", "req-1", "BOOK_SEAT", digest, reply);

        IdempotencyCache.LookupResult result = cache.lookup("user-1", "req-1", "BOOK_SEAT", digest);
        assertEquals(IdempotencyCache.LookupStatus.HIT, result.status());
        assertEquals(reply, result.cachedReply());
    }

    @Test
    @DisplayName("Detects CONFLICT when same requestId is re-used with different payload")
    void testReusedRequestIdWithDifferentPayloadIsConflict() {
        String payload1 = "{\"type\":\"BOOK_SEAT\",\"showId\":\"SHOW-01\",\"seatId\":\"A1\"}";
        String digest1 = IdempotencyCache.computeDigest(payload1);
        String reply = "{\"type\":\"BOOK_OK\",\"bookingId\":\"BKG-100\"}";

        cache.record("user-1", "req-1", "BOOK_SEAT", digest1, reply);

        String payload2 = "{\"type\":\"BOOK_SEAT\",\"showId\":\"SHOW-01\",\"seatId\":\"B2\"}";
        String digest2 = IdempotencyCache.computeDigest(payload2);

        IdempotencyCache.LookupResult result = cache.lookup("user-1", "req-1", "BOOK_SEAT", digest2);
        assertEquals(IdempotencyCache.LookupStatus.CONFLICT, result.status());
        assertNull(result.cachedReply());
    }

    @Test
    @DisplayName("Returns MISS when request is not found or has expired")
    void testMissAndTtlExpiration() {
        String digest = IdempotencyCache.computeDigest("sample");
        IdempotencyCache.LookupResult miss = cache.lookup("user-1", "req-unknown", "BOOK_SEAT", digest);
        assertEquals(IdempotencyCache.LookupStatus.MISS, miss.status());

        cache.record("user-1", "req-exp", "BOOK_SEAT", digest, "{\"type\":\"OK\"}");
        assertEquals(IdempotencyCache.LookupStatus.HIT, cache.lookup("user-1", "req-exp", "BOOK_SEAT", digest).status());

        // Advance clock past 60s TTL
        clock.advanceSeconds(65);

        IdempotencyCache.LookupResult expired = cache.lookup("user-1", "req-exp", "BOOK_SEAT", digest);
        assertEquals(IdempotencyCache.LookupStatus.MISS, expired.status());
    }

    @Test
    @DisplayName("Bounded capacity evicts oldest entries when capacity is reached")
    void testBoundedCapacity() {
        IdempotencyCache smallCache = new IdempotencyCache(2, 300, clock);
        smallCache.record("u1", "r1", "OP", "d1", "rep1");
        smallCache.record("u1", "r2", "OP", "d2", "rep2");
        smallCache.record("u1", "r3", "OP", "d3", "rep3");

        assertTrue(smallCache.size() <= 2);
    }
}
