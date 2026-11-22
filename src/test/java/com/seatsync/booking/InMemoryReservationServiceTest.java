package com.seatsync.booking;

import com.seatsync.common.model.ErrorCode;
import com.seatsync.common.model.SeatStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InMemoryReservationServiceTest {
    private static final String SHOW = "SHOW-01";
    private final InMemoryReservationService service = new InMemoryReservationService();

    @Test
    void initialInventoryHasFortyOrderedFreeSeats() {
        var snapshot = service.snapshot(SHOW);
        assertEquals(0, snapshot.version());
        assertEquals(40, snapshot.seats().size());
        assertEquals("A1", snapshot.seats().get(0).seatId());
        assertEquals("E8", snapshot.seats().get(39).seatId());
        assertTrue(snapshot.seats().stream().allMatch(s -> s.status() == SeatStatus.AVAILABLE && s.bookedBy() == null));
    }

    @Test
    void bookingDuplicateCancellationAndRebookingPreserveOwnership() {
        var booking = service.book("alice", SHOW, " a5 ");
        assertTrue(booking.success());
        assertEquals("A5", booking.seatId());
        assertEquals(ErrorCode.SEAT_ALREADY_BOOKED, service.book("bob", SHOW, "A5").errorCode());
        assertEquals(ErrorCode.NOT_BOOKING_OWNER, service.cancel("bob", booking.bookingId()).errorCode());
        assertEquals(1, service.snapshot(SHOW).version());
        assertEquals("alice", service.snapshot(SHOW).seats().get(4).bookedBy());
        assertTrue(service.cancel("alice", booking.bookingId()).success());
        assertEquals(ErrorCode.BOOKING_NOT_FOUND, service.cancel("alice", booking.bookingId()).errorCode());
        var rebooking = service.book("bob", SHOW, "A5");
        assertTrue(rebooking.success());
        assertNotEquals(booking.bookingId(), rebooking.bookingId());
        assertEquals(ErrorCode.BOOKING_NOT_FOUND, service.cancel("alice", booking.bookingId()).errorCode());
        assertEquals("bob", service.snapshot(SHOW).seats().get(4).bookedBy());
        assertEquals(3, service.snapshot(SHOW).version());
    }

    @Test
    void invalidInputsNeverMutateInventory() {
        assertEquals(ErrorCode.AUTH_REQUIRED, service.book(null, SHOW, "A1").errorCode());
        assertEquals(ErrorCode.AUTH_REQUIRED, service.book(" ", SHOW, "A1").errorCode());
        assertEquals(ErrorCode.SHOW_NOT_FOUND, service.book("alice", null, "A1").errorCode());
        assertEquals(ErrorCode.SHOW_NOT_FOUND, service.book("alice", "OTHER", "A1").errorCode());
        assertEquals(ErrorCode.SEAT_NOT_FOUND, service.book("alice", SHOW, null).errorCode());
        assertEquals(ErrorCode.SEAT_NOT_FOUND, service.book("alice", SHOW, "F1").errorCode());
        assertEquals(ErrorCode.AUTH_REQUIRED, service.cancel("", "missing").errorCode());
        assertEquals(ErrorCode.BOOKING_NOT_FOUND, service.cancel("alice", null).errorCode());
        assertThrows(IllegalArgumentException.class, () -> service.snapshot("OTHER"));
        assertThrows(IllegalArgumentException.class, () -> service.snapshot(null));
        assertEquals(0, service.snapshot(SHOW).version());
    }

    @Test
    void snapshotIsAnImmutablePointInTimeCopy() {
        var before = service.snapshot(SHOW);
        var booked = service.book("alice", SHOW, "A1");
        var during = service.snapshot(SHOW);
        service.cancel("alice", booked.bookingId());
        assertEquals(SeatStatus.AVAILABLE, before.seats().get(0).status());
        assertEquals(SeatStatus.BOOKED, during.seats().get(0).status());
        assertEquals(1, during.version());
        assertThrows(UnsupportedOperationException.class, () -> during.seats().clear());
        assertEquals(SeatStatus.AVAILABLE, service.snapshot(SHOW).seats().get(0).status());
    }
}
