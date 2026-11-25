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
import com.seatsync.common.model.MultiHoldResult;
import com.seatsync.common.model.ReleaseResult;
import java.util.function.Supplier;
import com.seatsync.common.contracts.WaitlistService;
import com.seatsync.common.model.WaitlistEntry;
import com.seatsync.common.model.WaitlistResult;
import com.seatsync.common.protocol.event.WaitlistPromotedEvent;
import java.util.function.Consumer;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.Optional;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import com.seatsync.common.contracts.AdminCatalogOperations;
import com.seatsync.common.model.MovieDetails;
import com.seatsync.common.model.AdminBookingView;
import com.seatsync.common.model.PagedResult;
import com.seatsync.common.model.AdminOperationException;
import com.seatsync.common.model.SeatStatus;
import com.seatsync.common.protocol.event.ShowUpdatedEvent;

/**
 * Authoritative in-memory catalog and inventory for scheduled shows.
 * One lock protects seats, reservations and version as a single transaction.
 * Callers supply identity from RequestContext, never from client JSON.
 */
public final class InMemoryReservationService implements ReservationService, CatalogService, HoldService, WaitlistService, AdminCatalogOperations {
    private final Object inventoryLock = new Object();
    private final Map<String, ShowInventory> shows = new LinkedHashMap<>();
    private final Map<String, MovieDetails> movies = new LinkedHashMap<>();
    private final List<Consumer<ShowUpdatedEvent>> catalogListeners = new CopyOnWriteArrayList<>();
    private final Map<String, Reservation> reservations = new HashMap<>();
    private final SeatEventPublisher events = new SeatEventPublisher();
    static final long MAX_HOLD_MS = 120_000;
    static final long OFFER_MS = 60_000;
    private static final int MAX_WAITLIST = 100;
    private final List<Consumer<WaitlistPromotedEvent>> offerListeners = new CopyOnWriteArrayList<>();
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
        movies.put("MOVIE-01", new MovieDetails("MOVIE-01", "The Phantom of the Opera", "Classic Broadway musical", "Musical", 150));
        movies.put("MOVIE-02", new MovieDetails("MOVIE-02", "Les Mis\u00e9rables", "Musical drama", "Musical", 180));
        movies.put("MOVIE-03", new MovieDetails("MOVIE-03", "The Lion King", "Family stage musical", "Musical", 130));
        var standard = new SeatTier("Standard", TierType.STANDARD, 2500, "Standard admission");
        var premium = new SeatTier("Premium", TierType.PREMIUM, 4000, "Premium admission");
        var grand = new AuditoriumLayout("LAYOUT-01", "Orchestra Hall", 5, 8, 40, List.of(standard));
        var main = new AuditoriumLayout("LAYOUT-02", "Main Hall", 10, 12, 120, List.of(standard, premium));
        var family = new AuditoriumLayout("LAYOUT-03", "Family Hall", 5, 8, 40, List.of(standard));
        registerShow(new ShowDetails(ProtocolConstants.DEFAULT_SHOW_ID, "The Phantom of the Opera",
            "Classic Broadway musical", "Musical", "2026-12-01T19:30:00Z", "2026-12-01T22:00:00Z", "Grand Stage", grand));
        registerShow(new ShowDetails("SHOW-02", "Les Mis\u00e9rables", "Musical drama", "Musical",
            "2026-12-02T20:00:00Z", "2026-12-02T23:00:00Z", "Main Hall", main));
        registerShow(new ShowDetails("SHOW-11", "The Phantom of the Opera",
            "Classic Broadway musical", "Musical", "2026-12-03T14:00:00Z", "2026-12-03T16:30:00Z", "Grand Stage", grand));
        registerShow(new ShowDetails("SHOW-12", "Les Mis\u00e9rables", "Musical drama", "Musical",
            "2026-12-04T19:00:00Z", "2026-12-04T22:00:00Z", "Main Hall", main));
        registerShow(new ShowDetails("SHOW-13", "The Lion King", "Family stage musical", "Musical",
            "2026-12-05T16:00:00Z", "2026-12-05T18:15:00Z", "Family Hall", family));
        registerShow(new ShowDetails("SHOW-14", "The Lion King", "Family stage musical", "Musical",
            "2026-12-06T18:30:00Z", "2026-12-06T20:45:00Z", "Family Hall", family));
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
            if (shows.size() >= 10) { throw new AdminOperationException(ErrorCode.INVALID_STATE, "Show catalog limit reached"); }
            shows.put(details.showId(), new ShowInventory(details));
            catalogChanged(shows.get(details.showId()));
        }
        events.drain();
    }

    @Override
    public List<ShowSummary> getShows() {
        return transaction(() -> shows.values().stream().filter(show -> show.enabled).map(ShowInventory::summary).toList());
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
        final Map<String, WaitlistEntry> waiting = new LinkedHashMap<>();
        // One outstanding private offer per user per show.
        final Map<String, String> offers = new HashMap<>();
        long version;
        boolean enabled = true;
        String movieId;

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
            int available = enabled ? (int) seats.values().stream().filter(Seat::isAvailable).count() : 0;
            return new ShowSummary(details.showId(), details.title(), details.startTime(), details.venue(),
                seats.size(), available);
        }

        SeatSnapshot snapshot() {
            return new SeatSnapshot(details.showId(), version, seats.values().stream().map(Seat::view).toList());
        }
    }

    public void addSeatChangeListener(SeatChangeListener listener) { events.add(listener); }
    public void removeSeatChangeListener(SeatChangeListener listener) { events.remove(listener); }

    public void addCatalogChangeListener(Consumer<ShowUpdatedEvent> listener) {
        catalogListeners.add(java.util.Objects.requireNonNull(listener));
    }

    private void catalogChanged(ShowInventory show) {
        var event = new ShowUpdatedEvent(show.details.showId(), show.summary(), show.version, show.enabled);
        for (var listener : catalogListeners) { events.enqueue(() -> listener.accept(event)); }
    }

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
            if (!show.enabled) { return BookingResult.rejected(showId, normalized, ErrorCode.SHOW_DISABLED, "Show disabled"); }
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
            offerOrFree(show, reservation.seatId());
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
            if (!show.enabled) { return HoldResult.rejected(showId, seatId, ErrorCode.SHOW_DISABLED, "Show disabled"); }
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

    @Override
    public MultiHoldResult holdSeats(String user, String showId, List<String> seatIds, long durationMs) {
        return transaction(() -> {
            if (user == null || user.isBlank()) {
                return MultiHoldResult.rejected(showId, seatIds, ErrorCode.AUTH_REQUIRED, "Login required");
            }
            if (seatIds == null || seatIds.isEmpty() || seatIds.size() > 8) {
                return MultiHoldResult.rejected(showId, seatIds, ErrorCode.INVALID_REQUEST_FORMAT,
                    "Choose between 1 and 8 seats");
            }
            if (seatIds.stream().distinct().count() != seatIds.size()) {
                return MultiHoldResult.rejected(showId, seatIds, ErrorCode.INVALID_REQUEST_FORMAT, "Duplicate seat");
            }
            if (durationMs <= 0) {
                return MultiHoldResult.rejected(showId, seatIds, ErrorCode.INVALID_REQUEST_FORMAT, "Positive duration required");
            }
            var show = shows.get(showId);
            if (show == null) {
                return MultiHoldResult.rejected(showId, seatIds, ErrorCode.SHOW_NOT_FOUND, "Unknown show");
            }
            if (!show.enabled) {
                return MultiHoldResult.rejected(showId, seatIds, ErrorCode.SHOW_DISABLED, "Show disabled");
            }
            for (String seatId : seatIds) {
                var seat = show.seats.get(seatId);
                if (seat == null) {
                    return MultiHoldResult.rejected(showId, List.of(seatId), ErrorCode.SEAT_NOT_FOUND, "Unknown seat");
                }
                if (!seat.isAvailable()) {
                    var code = seat.hold() == null ? ErrorCode.SEAT_ALREADY_BOOKED : ErrorCode.SEAT_ALREADY_HELD;
                    return MultiHoldResult.rejected(showId, List.of(seatId), code, "Seat unavailable");
                }
            }
            var holdIds = new java.util.ArrayList<String>();
            long expiresAt = 0L;
            for (String seatId : seatIds) {
                var hold = placeHold(user, show, seatId, Math.min(durationMs, MAX_HOLD_MS));
                holdIds.add(hold.holdId());
                expiresAt = hold.expirationTimestamp();
            }
            return MultiHoldResult.ok(showId, seatIds, holdIds, expiresAt, "Seats held");
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
        show.offers.remove(hold.heldByUserId(), hold.holdId());
        offerOrFree(show, hold.seatId());
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
            var hold = holds.get(holdId);
            var show = shows.get(hold.showId());
            if (!show.enabled) { return BookingResult.rejected(hold.showId(), hold.seatId(), ErrorCode.SHOW_DISABLED, "Show disabled"); }
            holds.remove(holdId);
            show.seats.get(hold.seatId()).setHold(null);
            show.offers.remove(user, holdId);
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

    public void addOfferListener(Consumer<WaitlistPromotedEvent> listener) {
        offerListeners.add(java.util.Objects.requireNonNull(listener));
    }

    private void offerOrFree(ShowInventory show, String seatId) {
        var iterator = show.waiting.values().iterator();
        if (!show.enabled || !iterator.hasNext()) {
            changed(show);
            return;
        }
        var next = iterator.next();
        iterator.remove();
        var hold = placeHold(next.userId(), show, seatId, OFFER_MS);
        show.offers.put(next.userId(), hold.holdId());
        var offer = new WaitlistPromotedEvent(next.showId(), seatId, next.userId(), hold.holdId(),
            hold.expirationTimestamp(), show.version);
        for (var listener : offerListeners) {
            events.enqueue(() -> listener.accept(offer));
        }
    }

    @Override
    public WaitlistResult joinWaitlist(String user, String showId) {
        return transaction(() -> {
            if (user == null || user.isBlank()) {
                return WaitlistResult.rejected(showId, ErrorCode.AUTH_REQUIRED, "Login required");
            }
            var show = shows.get(showId);
            if (show == null) { return WaitlistResult.rejected(showId, ErrorCode.SHOW_NOT_FOUND, "Unknown show"); }
            if (!show.enabled) { return WaitlistResult.rejected(showId, ErrorCode.SHOW_DISABLED, "Show disabled"); }
            if (show.waiting.containsKey(user) || show.offers.containsKey(user)) {
                return WaitlistResult.rejected(showId, ErrorCode.ALREADY_ON_WAITLIST, "Already waiting or offered a seat");
            }
            if (show.seats.values().stream().anyMatch(Seat::isAvailable)) {
                return WaitlistResult.rejected(showId, ErrorCode.INVALID_REQUEST_FORMAT, "Show has available seats");
            }
            if (show.waiting.size() >= MAX_WAITLIST) {
                return WaitlistResult.rejected(showId, ErrorCode.WAITLIST_FULL, "Waitlist full");
            }
            int position = show.waiting.size() + 1;
            String id = "WTL-" + UUID.randomUUID();
            show.waiting.put(user, new WaitlistEntry(id, showId, user, position, clock.currentTimeMillis()));
            return WaitlistResult.ok(id, showId, position, "Joined waitlist");
        });
    }

    @Override
    public WaitlistResult leaveWaitlist(String user, String showId) {
        return transaction(() -> {
            if (user == null || user.isBlank()) {
                return WaitlistResult.rejected(showId, ErrorCode.AUTH_REQUIRED, "Login required");
            }
            var show = shows.get(showId);
            if (show == null) { return WaitlistResult.rejected(showId, ErrorCode.SHOW_NOT_FOUND, "Unknown show"); }
            var removed = show.waiting.remove(user);
            if (removed == null) {
                return WaitlistResult.rejected(showId, ErrorCode.NOT_ON_WAITLIST, "No waiting entry");
            }
            return WaitlistResult.ok(removed.waitlistId(), showId, -1, "Left waitlist");
        });
    }

    private Optional<WaitlistEntry> entryAtCurrentPosition(ShowInventory show, String user) {
        int position = 0;
        for (var entry : show.waiting.values()) {
            position++;
            if (entry.userId().equals(user)) {
                return Optional.of(new WaitlistEntry(entry.waitlistId(), entry.showId(), user, position, entry.timestamp()));
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<WaitlistEntry> getWaitlistStatus(String user, String showId) {
        return transaction(() -> Optional.ofNullable(shows.get(showId)).flatMap(show -> entryAtCurrentPosition(show, user)));
    }

    @Override
    public int getQueuePosition(String user, String showId) {
        return getWaitlistStatus(user, showId).map(WaitlistEntry::position).orElse(-1);
    }

    @Override
    public Optional<Status> status(String user, String showId) {
        return transaction(() -> Optional.ofNullable(shows.get(showId)).map(show ->
            new Status(entryAtCurrentPosition(show, user).map(WaitlistEntry::position).orElse(-1), show.waiting.size())));
    }

    @Override public boolean createMovie(MovieDetails movie) {
        java.util.Objects.requireNonNull(movie);
        return transaction(() -> {
            if (movies.containsKey(movie.movieId())) { return false; }
            if (movies.size() >= 1000) { throw new AdminOperationException(ErrorCode.INVALID_STATE, "Movie catalog limit reached"); }
            movies.put(movie.movieId(), movie);
            return true;
        });
    }

    @Override public List<MovieDetails> getMovies() {
        return transaction(() -> List.copyOf(movies.values()));
    }

    @Override public boolean createShow(String movieId, ShowDetails details) {
        return transaction(() -> {
            var movie = movies.get(movieId);
            if (movie == null) { throw new AdminOperationException(ErrorCode.MOVIE_NOT_FOUND, "Unknown movie"); }
            if (shows.containsKey(details.showId())) { return false; }
            var layout = details.layout();
            if (layout.rowCount() < 1 || layout.rowCount() > 26 || layout.columnCount() < 1
                    || layout.totalCapacity() > 120 || (long) layout.rowCount() * layout.columnCount() != layout.totalCapacity()) {
                throw new IllegalArgumentException("Invalid layout");
            }
            var start = java.time.Instant.parse(details.startTime());
            var end = java.time.Instant.parse(details.endTime());
            if (start.toEpochMilli() <= clock.currentTimeMillis()
                    || java.time.Duration.between(start, end).toMinutes() < movie.durationMinutes()) {
                throw new AdminOperationException(ErrorCode.INVALID_STATE, "Show window must cover the movie and start in the future");
            }
            for (var existing : shows.values()) {
                if (existing.enabled && existing.details.venue().trim().equalsIgnoreCase(details.venue().trim())
                        && start.isBefore(java.time.Instant.parse(existing.details.endTime()))
                        && end.isAfter(java.time.Instant.parse(existing.details.startTime()))) {
                    throw new AdminOperationException(ErrorCode.INVALID_STATE, "Venue already scheduled for this time");
                }
            }
            if (shows.size() >= 10) { throw new AdminOperationException(ErrorCode.INVALID_STATE, "Show catalog limit reached"); }
            var canonical = new ShowDetails(details.showId(), movie.title(), movie.description(), movie.genre(),
                details.startTime(), details.endTime(), details.venue(), details.layout());
            var show = new ShowInventory(canonical);
            show.movieId = movieId;
            shows.put(canonical.showId(), show);
            catalogChanged(show);
            return true;
        });
    }

    @Override public boolean disableShow(String showId) {
        return transaction(() -> {
            var show = adminShow(showId);
            if (!show.enabled) { return false; }
            show.enabled = false;
            show.waiting.clear();
            show.offers.clear();
            for (var hold : List.copyOf(holds.values())) {
                if (hold.showId().equals(showId)) {
                    holds.remove(hold.holdId());
                    show.seats.get(hold.seatId()).setHold(null);
                }
            }
            // Existing confirmed reservations are retained for owner cancellation and admin inspection.
            changed(show);
            catalogChanged(show);
            return true;
        });
    }

    @Override public PagedResult<AdminBookingView> listBookings(String showId, int pageNumber, int pageSize) {
        if (pageNumber < 0 || pageSize < 1 || pageSize > 20) {
            throw new AdminOperationException(ErrorCode.INVALID_PAGE_SIZE, "Use nonnegative page and page size 1-20");
        }
        return transaction(() -> {
            adminShow(showId);
            var all = reservations.values().stream().filter(r -> r.showId().equals(showId))
                .sorted(java.util.Comparator.comparing(Reservation::bookingId)).toList();
            long offset = (long) pageNumber * pageSize;
            var page = all.stream().skip(offset).limit(pageSize)
                .map(r -> new AdminBookingView(r.bookingId(), r.showId(), r.seatId(), r.userId())).toList();
            return new PagedResult<>(page, pageNumber, pageSize, all.size(), offset + page.size() < all.size());
        });
    }

    @Override public boolean overrideSeat(String actorId, String showId, String seatId, SeatStatus status) {
        return transaction(() -> {
            var show = adminShow(showId);
            var seat = show.seats.get(seatId);
            if (seat == null) { throw new AdminOperationException(ErrorCode.SEAT_NOT_FOUND, "Unknown seat"); }
            if (status == null || actorId == null || actorId.isBlank()) { throw new IllegalArgumentException("Actor/status required"); }
            if (status == SeatStatus.AVAILABLE) {
                if (!show.waiting.isEmpty()) {
                    throw new AdminOperationException(ErrorCode.INVALID_STATE, "Use cancellation to preserve FIFO waitlist promotion");
                }
                if (seat.isAvailable()) { return true; }
                if (seat.hold() != null) {
                    var hold = seat.hold();
                    holds.remove(hold.holdId());
                    show.offers.remove(hold.heldByUserId(), hold.holdId());
                    seat.setHold(null);
                }
                if (seat.reservation() != null) { reservations.remove(seat.reservation().bookingId()); }
                seat.release();
                changed(show);
                return true;
            }
            if (!show.enabled) { throw new AdminOperationException(ErrorCode.SHOW_DISABLED, "Show disabled"); }
            if (!seat.isAvailable()) { throw new AdminOperationException(ErrorCode.INVALID_STATE, "Seat must be free before admin allocation"); }
            if (status == SeatStatus.BOOKED) { reserve(show, seatId, actorId); }
            else if (status == SeatStatus.HELD) { placeHold(actorId, show, seatId, MAX_HOLD_MS); }
            else { throw new IllegalArgumentException("Unsupported seat state"); }
            return true;
        });
    }

    @Override public boolean forceCancelHold(String holdId) {
        return transaction(() -> {
            var hold = holds.get(holdId);
            if (hold == null) { return false; }
            releaseLocked(hold);
            return true;
        });
    }

    private ShowInventory adminShow(String showId) {
        var show = shows.get(showId);
        if (show == null) { throw new AdminOperationException(ErrorCode.SHOW_NOT_FOUND, "Unknown show"); }
        return show;
    }
}
