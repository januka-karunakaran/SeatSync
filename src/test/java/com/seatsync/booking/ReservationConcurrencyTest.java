package com.seatsync.booking;

import com.seatsync.common.model.BookingResult;
import com.seatsync.common.model.ErrorCode;
import com.seatsync.common.model.SeatStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReservationConcurrencyTest {
    static <T> List<T> together(List<Callable<T>> operations) throws Exception {
        var pool = Executors.newFixedThreadPool(operations.size());
        var ready = new CountDownLatch(operations.size());
        var start = new CountDownLatch(1);
        try {
            var futures = operations.stream().map(operation -> pool.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return operation.call();
            })).toList();
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            List<T> results = new ArrayList<>();
            for (var future : futures) { results.add(future.get(5, TimeUnit.SECONDS)); }
            return results;
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void tenContendersHaveExactlyOneWinner() throws Exception {
        var service = new InMemoryReservationService();
        List<Callable<BookingResult>> calls = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String user = "user-" + i;
            calls.add(() -> service.book(user, "SHOW-01", "A5"));
        }
        var results = together(calls);
        assertEquals(1, results.stream().filter(BookingResult::success).count());
        assertEquals(9, results.stream().filter(r -> r.errorCode() == ErrorCode.SEAT_ALREADY_BOOKED).count());
        var state = service.snapshot("SHOW-01");
        assertEquals(1, state.version());
        assertEquals(1, state.seats().stream().filter(s -> s.status() == SeatStatus.BOOKED).count());
        var winner = results.stream().filter(BookingResult::success).findFirst().orElseThrow();
        assertTrue(service.cancel(state.seats().get(4).bookedBy(), winner.bookingId()).success());
    }

    @Test
    void differentSeatsAllBookWithUniqueReferences() throws Exception {
        var service = new InMemoryReservationService();
        List<Callable<BookingResult>> calls = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            String seat = "A" + i;
            calls.add(() -> service.book("alice", "SHOW-01", seat));
        }
        var results = together(calls);
        assertTrue(results.stream().allMatch(BookingResult::success));
        assertEquals(8, results.stream().map(BookingResult::bookingId).distinct().count());
        assertEquals(8, service.snapshot("SHOW-01").version());
    }

    @Test
    void cancellationAndBookingOverlapKeepIndexesConsistent() throws Exception {
        for (int attempt = 0; attempt < 20; attempt++) {
            var service = new InMemoryReservationService();
            var original = service.book("alice", "SHOW-01", "A5");
            List<Callable<Object>> calls = List.of(
                () -> service.cancel("alice", original.bookingId()),
                () -> service.book("bob", "SHOW-01", "A5"));
            var results = together(calls);
            var replacement = (BookingResult) results.get(1);
            var snapshot = service.snapshot("SHOW-01");
            assertEquals(replacement.success() ? SeatStatus.BOOKED : SeatStatus.AVAILABLE,
                snapshot.seats().get(4).status());
            assertEquals(replacement.success() ? 3 : 2, snapshot.version());
            assertEquals(ErrorCode.BOOKING_NOT_FOUND, service.cancel("alice", original.bookingId()).errorCode());
            if (replacement.success()) {
                assertEquals("bob", snapshot.seats().get(4).bookedBy());
                assertTrue(service.cancel("bob", replacement.bookingId()).success());
            } else {
                assertEquals(ErrorCode.SEAT_ALREADY_BOOKED, replacement.errorCode());
                assertTrue(service.book("bob", "SHOW-01", "A5").success());
            }
        }
    }
}
