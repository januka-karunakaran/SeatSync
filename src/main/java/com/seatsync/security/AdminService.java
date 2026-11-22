package com.seatsync.security;

import com.seatsync.common.contracts.AdminManagementService;
import com.seatsync.common.contracts.CatalogService;
import com.seatsync.common.contracts.AdminCatalogOperations;
import com.seatsync.common.model.MovieDetails;
import com.seatsync.common.model.AdminBookingView;
import com.seatsync.common.model.PagedResult;
import com.seatsync.common.model.AuditLogEntry;
import com.seatsync.common.model.SeatStatus;
import com.seatsync.common.model.ShowDetails;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Admin policy over the single booking source of truth; owns no seat/catalog copy. */
public final class AdminService implements AdminManagementService {
    private final AuthorizationService authorization;
    private final CatalogService catalog;
    private final Consumer<ShowDetails> register;
    private final AdminManagementService bookingAdmin;
    private final SecurityAuditLog audit;
    private final Clock clock;
    private final Consumer<ShowDetails> showChanged;

    public AdminService(AuthorizationService authorization, CatalogService catalog, Consumer<ShowDetails> register,
            AdminManagementService bookingAdmin, SecurityAuditLog audit, Clock clock, Consumer<ShowDetails> showChanged) {
        this.authorization = Objects.requireNonNull(authorization);
        this.catalog = Objects.requireNonNull(catalog);
        this.register = Objects.requireNonNull(register);
        this.bookingAdmin = bookingAdmin;
        this.audit = Objects.requireNonNull(audit);
        this.clock = Objects.requireNonNull(clock);
        this.showChanged = Objects.requireNonNull(showChanged);
    }

    @Override public boolean addShow(String actor, ShowDetails show) {
        authorization.requireAdmin(actor);
        validate(show);
        if (catalog.getShowDetails(show.showId()).isPresent()) { return false; }
        // The catalog's register operation enforces uniqueness atomically under its own lock.
        register.accept(show);
        try { showChanged.accept(show); }
        catch (RuntimeException ignored) { /* Notification failure cannot roll back a committed show. */ }
        return true;
    }

    @Override public boolean updateSeatStatus(String actor, String showId, String seatId, SeatStatus status) {
        authorization.requireAdmin(actor);
        identifier(showId); identifier(seatId); Objects.requireNonNull(status);
        return backend().updateSeatStatus(actor, showId, seatId, status);
    }

    @Override public boolean cancelHold(String actor, String holdId, String reason) {
        authorization.requireAdmin(actor);
        identifier(holdId);
        if (reason == null || reason.isBlank() || reason.length() > 256 || reason.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid cancellation reason");
        }
        return backend().cancelHold(actor, holdId, reason);
    }

    @Override public List<AuditLogEntry> getAuditLogs(String actor, int limit) {
        authorization.requireAdmin(actor);
        return audit.recent(limit);
    }

    private AdminManagementService backend() {
        if (bookingAdmin == null) { throw new UnsupportedOperationException("Booking admin backend is not installed"); }
        return bookingAdmin;
    }

    public boolean createMovie(String actor, MovieDetails movie) {
        authorization.requireAdmin(actor);
        return operations().createMovie(Objects.requireNonNull(movie));
    }

    public boolean createShow(String actor, String movieId, ShowDetails show) {
        authorization.requireAdmin(actor);
        identifier(movieId); validate(show);
        boolean created = operations().createShow(movieId, show);
        if (created) {
            try { showChanged.accept(catalog.getShowDetails(show.showId()).orElseThrow()); }
            catch (RuntimeException ignored) { /* The committed show remains authoritative. */ }
        }
        return created;
    }

    public boolean disableShow(String actor, String showId) {
        authorization.requireAdmin(actor);
        identifier(showId);
        return operations().disableShow(showId);
    }

    public PagedResult<AdminBookingView> listBookings(String actor, String showId, int page, int size) {
        authorization.requireAdmin(actor);
        identifier(showId);
        return operations().listBookings(showId, page, size);
    }

    public PagedResult<MovieDetails> movies(int page, int size) {
        if (page < 0 || size < 1 || size > 5) {
            throw new com.seatsync.common.model.AdminOperationException(com.seatsync.common.model.ErrorCode.INVALID_PAGE_SIZE,
                "Movie page size must be 1-5 with a nonnegative page");
        }
        var all = operations().getMovies();
        long offset = (long) page * size;
        var items = all.stream().skip(offset).limit(size).toList();
        return new PagedResult<>(items, page, size, all.size(), offset + items.size() < all.size());
    }

    private AdminCatalogOperations operations() {
        if (catalog instanceof AdminCatalogOperations operations) { return operations; }
        throw new UnsupportedOperationException("Admin catalog backend is not installed");
    }

    private void validate(ShowDetails show) {
        Objects.requireNonNull(show);
        identifier(show.showId());
        bounded(show.title(), 120); bounded(show.venue(), 120);
        bounded(show.genre(), 80); bounded(show.description(), 1024);
        Instant start;
        Instant end;
        try {
            start = Instant.parse(show.startTime());
            end = Instant.parse(show.endTime());
        } catch (java.time.DateTimeException | NullPointerException invalid) {
            throw new IllegalArgumentException("Invalid show time");
        }
        if (!start.isAfter(clock.instant()) || !end.isAfter(start)) {
            throw new IllegalArgumentException("Show must start in the future and end after its start");
        }
        var layout = show.layout();
        identifier(layout.layoutId()); bounded(layout.name(), 120);
        // Full snapshots must fit within the existing 16 KiB frame limit.
        if (layout.rowCount() < 1 || layout.rowCount() > 26 || layout.columnCount() < 1
                || layout.totalCapacity() > 120
                || (long) layout.rowCount() * layout.columnCount() != layout.totalCapacity()
                || layout.tiers().size() > 8) { throw new IllegalArgumentException("Invalid auditorium capacity"); }
        for (var tier : layout.tiers()) {
            bounded(tier.tierName(), 80);
            if (tier.priceInCents() < 0) { throw new IllegalArgumentException("Invalid ticket price"); }
        }
    }

    private static void identifier(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._-]{1,64}")) { throw new IllegalArgumentException("Invalid identifier"); }
    }
    private static void bounded(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid show metadata");
        }
    }
}
