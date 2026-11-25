package com.seatsync.booking;

import com.seatsync.common.model.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeatHoldTest {
    @Test
    void exactExpiryOwnerChecksAndReplay() {
        var time = new AtomicLong(1000);
        var service = new InMemoryReservationService(time::get);
        var first = service.holdSeat("alice", "SHOW-01", "A1", 200_000);
        assertEquals(121_000, first.expiresAt());
        assertEquals(ErrorCode.SEAT_ALREADY_HELD, service.book("bob", "SHOW-01", "A1").errorCode());
        assertEquals(ErrorCode.NOT_HOLD_OWNER, service.releaseHold("bob", first.holdId()).errorCode());
        assertEquals(ErrorCode.NOT_HOLD_OWNER, service.confirmHold("bob", first.holdId(), "key").errorCode());
        time.set(first.expiresAt());
        assertEquals(ErrorCode.HOLD_EXPIRED, service.confirmHold("alice", first.holdId(), "key").errorCode());
        var second = service.holdSeat("bob", "SHOW-01", "A1", 100);
        var booking = service.confirmHold("bob", second.holdId(), "key");
        assertTrue(booking.success());
        assertEquals(booking, service.confirmHold("bob", second.holdId(), "key"));
        assertEquals(SeatStatus.BOOKED, service.snapshot("SHOW-01").seats().get(0).status());
        var third = service.holdSeat("bob", "SHOW-02", "A1", 100);
        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, service.confirmHold("bob", third.holdId(), "key").errorCode());
        assertTrue(service.releaseHold("bob", third.holdId()).success());
        assertEquals(SeatStatus.AVAILABLE, service.snapshot("SHOW-02").seats().get(0).status());
    }

    @Test
    void overlappingHoldsHaveOneWinner() throws Exception {
        var service = new InMemoryReservationService();
        var pool = Executors.newFixedThreadPool(10);
        var start = new CyclicBarrier(10);
        try {
            var results = new ArrayList<Future<HoldResult>>();
            for (int i = 0; i < 10; i++) {
                String user = "user-" + i;
                results.add(pool.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    return service.holdSeat(user, "SHOW-01", "A1", 120_000);
                }));
            }
            int winners = 0;
            for (var future : results) {
                var result = future.get(5, TimeUnit.SECONDS);
                if (result.success()) { winners++; }
                else { assertEquals(ErrorCode.SEAT_ALREADY_HELD, result.errorCode()); }
            }
            assertEquals(1, winners);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void multiSeatHoldIsAllOrNothingAndShowScoped() {
        var service = new InMemoryReservationService();
        assertTrue(service.book("alice", "SHOW-01", "A2").success());
        var rejected = service.holdSeats("bob", "SHOW-01", java.util.List.of("A1", "A2"), 120_000);
        assertFalse(rejected.success());
        assertEquals(SeatStatus.AVAILABLE, service.snapshot("SHOW-01").seats().stream()
            .filter(seat -> seat.seatId().equals("A1")).findFirst().orElseThrow().status());

        var held = service.holdSeats("bob", "SHOW-01", java.util.List.of("B1", "B2"), 120_000);
        assertTrue(held.success());
        assertEquals(2, held.holdIds().size());
        assertEquals(SeatStatus.HELD, service.snapshot("SHOW-01").seats().stream()
            .filter(seat -> seat.seatId().equals("B1")).findFirst().orElseThrow().status());
        assertEquals(SeatStatus.AVAILABLE, service.snapshot("SHOW-11").seats().stream()
            .filter(seat -> seat.seatId().equals("B1")).findFirst().orElseThrow().status());
        assertEquals(6, service.getShows().size());
    }
}
