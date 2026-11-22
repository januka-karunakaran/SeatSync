package com.seatsync.booking;

import com.seatsync.common.contracts.CatalogService;
import com.seatsync.common.contracts.ReservationService;
import com.seatsync.common.contracts.SeatChangeListener;
import com.seatsync.common.model.BookingResult;
import com.seatsync.common.model.CancelResult;
import com.seatsync.common.model.ErrorCode;
import com.seatsync.common.model.ProtocolConstants;
import com.seatsync.common.model.SeatSnapshot;
import com.seatsync.common.model.AuditoriumLayout;
import com.seatsync.common.model.ShowDetails;
import com.seatsync.common.model.ShowSummary;
import com.seatsync.common.model.SeatTier;
import com.seatsync.common.model.TierType;
import java.util.List;
import java.util.Optional;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Authoritative in-memory catalog and inventory for scheduled shows.
 * One lock protects seats, reservations and version as a single transaction.
 * Callers supply identity from RequestContext, never from client JSON.
 */
public final class InMemoryReservationService implements ReservationService, CatalogService {
    private final Object inventoryLock = new Object();
    private final Map<String, ShowInventory> shows = new LinkedHashMap<>();
    private final Map<String, Reservation> reservations = new HashMap<>();
    private final SeatEventPublisher events = new SeatEventPublisher();
    public InMemoryReservationService() {
        var standard = new SeatTier("Standard", TierType.STANDARD, 2500, "Standard admission");
        var premium = new SeatTier("Premium", TierType.PREMIUM, 4000, "Premium admission");
        registerShow(new ShowDetails(ProtocolConstants.DEFAULT_SHOW_ID, "The Phantom of the Opera",
            "Classic Broadway musical", "Musical", "2026-12-01T19:30:00Z", "2026-12-01T22:00:00Z", "Grand Stage",
            new AuditoriumLayout("LAYOUT-01", "Orchestra Hall", 5, 8, 40, List.of(standard))));
        registerShow(new ShowDetails("SHOW-02", "Les Mis\u00e9rables", "Musical drama", "Musical",
            "2026-12-02T20:00:00Z", "2026-12-02T23:00:00Z", "Main Hall",
            new AuditoriumLayout("LAYOUT-02", "Main Hall", 10, 12, 120, List.of(standard, premium))));
    }

    public void registerShow(ShowDetails details) {
        var layout = details.layout();
        if (details.showId().isBlank() || layout.rowCount() < 1 || layout.rowCount() > 26
                || layout.columnCount() < 1 || (long) layout.rowCount() * layout.columnCount() != layout.totalCapacity()) {
            throw new IllegalArgumentException("Expected a show ID and a rectangular layout with rows A through Z");
        }
        synchronized (inventoryLock) {
            if (shows.containsKey(details.showId())) {
                throw new IllegalArgumentException("Show already registered");
            }
            shows.put(details.showId(), new ShowInventory(details));
        }
    }

    @Override
    public List<ShowSummary> getShows() {
        synchronized (inventoryLock) {
            return shows.values().stream().map(ShowInventory::summary).toList();
        }
    }

    @Override
    public Optional<ShowDetails> getShowDetails(String showId) {
        synchronized (inventoryLock) {
            return Optional.ofNullable(shows.get(showId)).map(show -> show.details);
        }
    }

    private static final class ShowInventory {
        final ShowDetails details;
        final Map<String, Seat> seats = new LinkedHashMap<>();
        long version;

        ShowInventory(ShowDetails details) {
            this.details = details;
            var layout = details.layout();
            for (int row = 0; row < layout.rowCount(); row++) {
                for (int column = 1; column <= layout.columnCount(); column++) {
                    String id = Character.toString('A' + row) + column;
                    seats.put(id, new Seat(id));
                }
            }
        }

        ShowSummary summary() {
            int available = (int) seats.values().stream().filter(Seat::isAvailable).count();
            return new ShowSummary(details.showId(), details.title(), details.startTime(), details.venue(),
                seats.size(), available);
        }

        SeatSnapshot snapshot() {
            return new SeatSnapshot(details.showId(), version, seats.values().stream().map(Seat::view).toList());
        }
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
            var show = shows.get(showId);
            if (show == null) {
                return BookingResult.rejected(showId, normalized, ErrorCode.SHOW_NOT_FOUND, "Unknown show");
            }
            Seat seat = show.seats.get(normalized);
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
            show.version++;
            events.enqueue(show.snapshot());
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
            var show = shows.get(reservation.showId());
            show.seats.get(reservation.seatId()).release();
            reservations.remove(bookingId);
            show.version++;
            events.enqueue(show.snapshot());
            result = CancelResult.ok(bookingId, reservation.seatId(), "Booking cancelled successfully");
        }
        events.drain();
        return result;
    }

    @Override
    public SeatSnapshot snapshot(String showId) {
        synchronized (inventoryLock) {
            var show = shows.get(showId);
            if (show == null) {
                throw new IllegalArgumentException("Unknown show");
            }
            return show.snapshot();
        }
    }
}
