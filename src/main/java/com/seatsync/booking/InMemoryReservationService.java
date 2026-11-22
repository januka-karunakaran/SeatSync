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
import com.seatsync.common.contracts.ClockService;
import com.seatsync.common.contracts.HoldService;
import com.seatsync.common.model.SeatHold;
import com.seatsync.common.model.HoldResult;
import com.seatsync.common.model.ReleaseResult;
import java.util.function.Supplier;
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
public final class InMemoryReservationService implements ReservationService, CatalogService, HoldService {
    private final Object inventoryLock = new Object();
    private final Map<String, ShowInventory> shows = new LinkedHashMap<>();
    private final Map<String, Reservation> reservations = new HashMap<>();
    private final SeatEventPublisher events = new SeatEventPublisher();
    static final long MAX_HOLD_MS = 120_000;
    private final ClockService clock;
    private final Map<String, SeatHold> holds = new LinkedHashMap<>();
    private final Map<String, Long> expired = new HashMap<>();
    private record ReplayKey(String user, String key) {}
    private record Confirmation(String holdId, BookingResult result) {}
    private final Map<ReplayKey, Confirmation> confirmations = new HashMap<>();

    public InMemoryReservationService() {
        this(ClockService.system());
    }

    InMemoryReservationService(ClockService clock) {
        this.clock = clock;
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
        return transaction(() -> shows.values().stream().map(ShowInventory::summary).toList());
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
        return transaction(() -> {
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
            if (seat.hold() != null) {
                return BookingResult.rejected(showId, normalized, ErrorCode.SEAT_ALREADY_HELD, "Seat held");
            }
            if (!seat.isAvailable()) {
                return BookingResult.rejected(showId, normalized, ErrorCode.SEAT_ALREADY_BOOKED, "Seat already booked");
            }
            return reserve(show, normalized, userId);
        });
    }

    private BookingResult reserve(ShowInventory show, String seatId, String user) {
        String showId = show.details.showId();
        String bookingId = "BKG-" + UUID.randomUUID();
        var reservation = new Reservation(bookingId, showId, seatId, user);
        show.seats.get(seatId).reserve(reservation);
        reservations.put(bookingId, reservation);
        changed(show);
        return BookingResult.ok(bookingId, showId, seatId, "Seat booked successfully");
    }

    private void changed(ShowInventory show) {
        show.version++;
        events.enqueue(show.snapshot());
    }

    @Override
    public CancelResult cancel(String userId, String bookingId) {
        return transaction(() -> {
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
            changed(show);
            return CancelResult.ok(bookingId, reservation.seatId(), "Booking cancelled successfully");
        });
    }

    @Override
    public SeatSnapshot snapshot(String showId) {
        return transaction(() -> {
            var show = shows.get(showId);
            if (show == null) {
                throw new IllegalArgumentException("Unknown show");
            }
            return show.snapshot();
        });
    }

    // Check deadlines under the same lock as all readers and writers, even between scheduler ticks.
    private <T> T transaction(Supplier<T> operation) {
        try {
            synchronized (inventoryLock) {
                long now = clock.currentTimeMillis();
                expired.values().removeIf(forgetAt -> forgetAt <= now);
                for (var hold : List.copyOf(holds.values())) {
                    if (hold.isExpired(now)) {
                        expired.put(hold.holdId(), now + MAX_HOLD_MS);
                        releaseLocked(hold);
                    }
                }
                return operation.get();
            }
        } finally {
            events.drain();
        }
    }

    public void expireHolds() {
        transaction(() -> null);
    }

    @Override
    public HoldResult holdSeat(String user, String showId, String seatId, long durationMs) {
        return transaction(() -> {
            if (user == null || user.isBlank()) {
                return HoldResult.rejected(showId, seatId, ErrorCode.AUTH_REQUIRED, "Login required");
            }
            var show = shows.get(showId);
            if (show == null) {
                return HoldResult.rejected(showId, seatId, ErrorCode.SHOW_NOT_FOUND, "Unknown show");
            }
            var seat = show.seats.get(seatId);
            if (seat == null) {
                return HoldResult.rejected(showId, seatId, ErrorCode.SEAT_NOT_FOUND, "Unknown seat");
            }
            if (!seat.isAvailable()) {
                var code = seat.hold() == null ? ErrorCode.SEAT_ALREADY_BOOKED : ErrorCode.SEAT_ALREADY_HELD;
                return HoldResult.rejected(showId, seatId, code, "Seat unavailable");
            }
            if (durationMs <= 0) {
                return HoldResult.rejected(showId, seatId, ErrorCode.INVALID_REQUEST_FORMAT, "Positive duration required");
            }
            var hold = placeHold(user, show, seatId, Math.min(durationMs, MAX_HOLD_MS));
            return HoldResult.ok(hold.holdId(), showId, seatId, hold.expirationTimestamp(), "Seat held");
        });
    }

    private SeatHold placeHold(String user, ShowInventory show, String seatId, long durationMs) {
        long now = clock.currentTimeMillis();
        var hold = new SeatHold("HLD-" + UUID.randomUUID(), show.details.showId(), seatId, user,
            now, now + durationMs, false);
        holds.put(hold.holdId(), hold);
        show.seats.get(seatId).setHold(hold);
        changed(show);
        return hold;
    }

    private ErrorCode holdError(String user, String holdId) {
        if (user == null || user.isBlank()) { return ErrorCode.AUTH_REQUIRED; }
        var hold = holds.get(holdId);
        if (hold == null) { return expired.containsKey(holdId) ? ErrorCode.HOLD_EXPIRED : ErrorCode.HOLD_NOT_FOUND; }
        return hold.heldByUserId().equals(user) ? null : ErrorCode.NOT_HOLD_OWNER;
    }

    private void releaseLocked(SeatHold hold) {
        var show = shows.get(hold.showId());
        holds.remove(hold.holdId());
        show.seats.get(hold.seatId()).setHold(null);
        changed(show);
    }

    @Override
    public ReleaseResult releaseHold(String user, String holdId) {
        return transaction(() -> {
            var error = holdError(user, holdId);
            if (error != null) { return ReleaseResult.rejected(holdId, error, "Cannot release hold"); }
            var hold = holds.get(holdId);
            releaseLocked(hold);
            return ReleaseResult.ok(holdId, hold.showId(), hold.seatId(), "Hold released");
        });
    }

    @Override
    public BookingResult confirmHold(String user, String holdId, String key) {
        return transaction(() -> {
            if (key == null || key.isBlank()) {
                return BookingResult.rejected(null, null, ErrorCode.INVALID_REQUEST_FORMAT, "Confirmation key required");
            }
            var replayKey = new ReplayKey(user, key);
            var previous = confirmations.get(replayKey);
            if (previous != null) {
                return previous.holdId().equals(holdId) ? previous.result()
                    : BookingResult.rejected(null, null, ErrorCode.IDEMPOTENCY_CONFLICT, "Key already used");
            }
            var error = holdError(user, holdId);
            if (error != null) { return BookingResult.rejected(null, null, error, "Cannot confirm hold"); }
            var hold = holds.remove(holdId);
            var show = shows.get(hold.showId());
            show.seats.get(hold.seatId()).setHold(null);
            var result = reserve(show, hold.seatId(), user);
            confirmations.put(replayKey, new Confirmation(holdId, result));
            return result;
        });
    }

    @Override
    public Optional<SeatHold> getHold(String holdId) {
        return transaction(() -> Optional.ofNullable(holds.get(holdId)));
    }

    @Override
    public List<SeatHold> getActiveHolds(String showId) {
        return transaction(() -> holds.values().stream().filter(h -> h.showId().equals(showId)).toList());
    }
}
