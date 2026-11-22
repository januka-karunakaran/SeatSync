package com.seatsync.booking;

import com.seatsync.common.contracts.ReservationService;
import com.seatsync.common.contracts.SeatChangeListener;
import com.seatsync.common.model.BookingResult;
import com.seatsync.common.model.CancelResult;
import com.seatsync.common.model.ErrorCode;
import com.seatsync.common.model.ProtocolConstants;
import com.seatsync.common.model.SeatSnapshot;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Member 04: authoritative, in-memory inventory for SHOW-01.
 * One lock protects seats, reservations and version as a single transaction.
 * Callers supply identity from RequestContext, never from client JSON.
 */
public final class InMemoryReservationService implements ReservationService {
    private final Object inventoryLock = new Object();
    private final Map<String, Seat> seats = new LinkedHashMap<>();
    private final Map<String, Reservation> reservations = new HashMap<>();
    private final SeatEventPublisher events = new SeatEventPublisher();
    private long version;

    public InMemoryReservationService() {
        ProtocolConstants.VALID_SEAT_IDS.forEach(id -> seats.put(id, new Seat(id)));
    }

    public void addSeatChangeListener(SeatChangeListener listener) { events.add(listener); }
    public void removeSeatChangeListener(SeatChangeListener listener) { events.remove(listener); }

    @Override
    public BookingResult book(String userId, String showId, String seatId) {
        String normalized = seatId == null ? null : seatId.trim().toUpperCase(Locale.ROOT);
        BookingResult result;
        synchronized (inventoryLock) {
            if (userId == null || userId.isBlank()) {
                return BookingResult.rejected(showId, normalized, ErrorCode.AUTH_REQUIRED, "Login required");
            }
            if (!ProtocolConstants.DEFAULT_SHOW_ID.equals(showId)) {
                return BookingResult.rejected(showId, normalized, ErrorCode.SHOW_NOT_FOUND, "Unknown show");
            }
            Seat seat = seats.get(normalized);
            if (seat == null) {
                return BookingResult.rejected(showId, normalized, ErrorCode.SEAT_NOT_FOUND, "Unknown seat");
            }
            if (!seat.isAvailable()) {
                return BookingResult.rejected(showId, normalized, ErrorCode.SEAT_ALREADY_BOOKED, "Seat already booked");
            }
            String bookingId = "BKG-" + UUID.randomUUID();
            Reservation reservation = new Reservation(bookingId, showId, normalized, userId);
            seat.reserve(reservation);
            reservations.put(bookingId, reservation);
            version++;
            events.enqueue(snapshotLocked());
            result = BookingResult.ok(bookingId, showId, normalized, "Seat booked successfully");
        }
        events.drain();
        return result;
    }

    @Override
    public CancelResult cancel(String userId, String bookingId) {
        CancelResult result;
        synchronized (inventoryLock) {
            if (userId == null || userId.isBlank()) {
                return CancelResult.rejected(bookingId, ErrorCode.AUTH_REQUIRED, "Login required");
            }
            Reservation reservation = reservations.get(bookingId);
            if (reservation == null) {
                return CancelResult.rejected(bookingId, ErrorCode.BOOKING_NOT_FOUND, "Booking not found");
            }
            if (!reservation.userId().equals(userId)) {
                return CancelResult.rejected(bookingId, ErrorCode.NOT_BOOKING_OWNER, "Booking belongs to another user");
            }
            seats.get(reservation.seatId()).release();
            reservations.remove(bookingId);
            version++;
            events.enqueue(snapshotLocked());
            result = CancelResult.ok(bookingId, reservation.seatId(), "Booking cancelled successfully");
        }
        events.drain();
        return result;
    }

    /** @throws IllegalArgumentException if showId is not SHOW-01. */
    @Override
    public SeatSnapshot snapshot(String showId) {
        if (!ProtocolConstants.DEFAULT_SHOW_ID.equals(showId)) {
            throw new IllegalArgumentException("Unknown show");
        }
        synchronized (inventoryLock) {
            return snapshotLocked();
        }
    }

    private SeatSnapshot snapshotLocked() {
        return new SeatSnapshot(ProtocolConstants.DEFAULT_SHOW_ID, version,
            seats.values().stream().map(Seat::view).toList());
    }
}
