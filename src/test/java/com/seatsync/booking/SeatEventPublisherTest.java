package com.seatsync.booking;

import com.seatsync.common.contracts.SeatChangeListener;
import com.seatsync.common.model.SeatSnapshot;
import com.seatsync.common.model.SeatStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeatEventPublisherTest {
    @Test
    void twoSubscribersReceiveOnlyCommittedImmutableSnapshots() {
        var service = new InMemoryReservationService();
        List<SeatSnapshot> first = new ArrayList<>();
        List<SeatSnapshot> second = new ArrayList<>();
        SeatChangeListener listener = (show, version, seats) -> first.add(new SeatSnapshot(show, version, seats));
        service.addSeatChangeListener(listener);
        service.addSeatChangeListener(listener); // Duplicate registration must not duplicate events.
        service.addSeatChangeListener((show, version, seats) -> second.add(new SeatSnapshot(show, version, seats)));
        var booking = service.book("alice", "SHOW-01", "A5");
        service.book("bob", "SHOW-01", "A5");
        service.cancel("bob", booking.bookingId());
        service.cancel("alice", booking.bookingId());
        assertEquals(first, second);
        assertEquals(List.of(1L, 2L), first.stream().map(SeatSnapshot::version).toList());
        assertEquals(40, first.get(0).seats().size());
        assertEquals(SeatStatus.BOOKED, first.get(0).seats().get(4).status());
        assertEquals(SeatStatus.AVAILABLE, first.get(1).seats().get(4).status());
        assertThrows(UnsupportedOperationException.class, () -> first.get(0).seats().clear());
        service.removeSeatChangeListener(listener);
        service.book("alice", "SHOW-01", "A5");
        assertEquals(2, first.size());
        assertEquals(3, second.size());
    }

    @Test
    void listenerFailureDoesNotRollbackOrPreventOtherListeners() {
        var service = new InMemoryReservationService();
        List<Long> received = new ArrayList<>();
        service.addSeatChangeListener((s, v, seats) -> { throw new IllegalStateException("subscriber unavailable"); });
        service.addSeatChangeListener((s, v, seats) -> received.add(v));
        var booking = service.book("alice", "SHOW-01", "A1");
        assertTrue(booking.success());
        assertTrue(service.cancel("alice", booking.bookingId()).success());
        assertEquals(List.of(1L, 2L), received);
    }

    @Test
    void slowListenerDoesNotHoldInventoryLockAndDeliveryStaysOrdered() throws Exception {
        var service = new InMemoryReservationService();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        List<Long> received = new CopyOnWriteArrayList<>();
        service.addSeatChangeListener((s, v, seats) -> {
            if (v == 1) {
                entered.countDown();
                try {
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
            }
            received.add(v);
        });
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> service.book("alice", "SHOW-01", "A1"));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            // Must finish while the first callback is blocked: proves callbacks run outside the lock.
            var second = pool.submit(() -> service.book("bob", "SHOW-01", "A2"));
            assertTrue(second.get(2, TimeUnit.SECONDS).success());
            assertEquals(2, service.snapshot("SHOW-01").version());
            release.countDown();
            assertTrue(first.get(5, TimeUnit.SECONDS).success());
            assertEquals(List.of(1L, 2L), received);
        } finally {
            release.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void concurrentCommitsDeliverVersionsInOrderToBothSubscribers() throws Exception {
        var service = new InMemoryReservationService();
        List<Long> first = new CopyOnWriteArrayList<>();
        List<Long> second = new CopyOnWriteArrayList<>();
        service.addSeatChangeListener((s, v, seats) -> first.add(v));
        service.addSeatChangeListener((s, v, seats) -> second.add(v));
        List<Callable<Boolean>> calls = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            String seat = "B" + i;
            calls.add(() -> service.book("alice", "SHOW-01", seat).success());
        }
        assertTrue(ReservationConcurrencyTest.together(calls).stream().allMatch(Boolean::booleanValue));
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L), first);
        assertEquals(first, second);
    }

    @Test
    void reentrantListenerQueuesNewMutationAfterCurrentEventForAllSubscribers() {
        var service = new InMemoryReservationService();
        List<Long> first = new ArrayList<>();
        List<Long> second = new ArrayList<>();
        service.addSeatChangeListener((s, v, seats) -> {
            first.add(v);
            if (v == 1) { assertTrue(service.book("bob", "SHOW-01", "A2").success()); }
        });
        service.addSeatChangeListener((s, v, seats) -> second.add(v));
        assertTrue(service.book("alice", "SHOW-01", "A1").success());
        assertEquals(List.of(1L, 2L), first);
        assertEquals(first, second);
    }
}
