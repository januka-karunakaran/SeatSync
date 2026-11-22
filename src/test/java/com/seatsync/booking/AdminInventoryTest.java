package com.seatsync.booking;

import com.seatsync.common.model.*;
import com.seatsync.common.protocol.event.ShowUpdatedEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdminInventoryTest {
    private static final MovieDetails MOVIE = new MovieDetails("NEW-MOVIE", "Movie", "Description", "Drama", 90);
    private static ShowDetails show(String id, int columns) {
        return new ShowDetails(id, "Title", "Description", "Drama", "2030-01-01T18:00:00Z", "2030-01-01T20:00:00Z",
            "New Room", new AuditoriumLayout("L", "Room", 1, columns, columns, List.of()));
    }

    @Test void concurrentMovieAndShowCreationHaveOneWinnerAndOneCatalogEvent() throws Exception {
        var service = new InMemoryReservationService();
        List<Callable<Boolean>> movies = new ArrayList<>();
        for (int i = 0; i < 10; i++) { movies.add(() -> service.createMovie(MOVIE)); }
        assertEquals(1, ReservationConcurrencyTest.together(movies).stream().filter(Boolean::booleanValue).count());
        List<ShowUpdatedEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();
        service.addCatalogChangeListener(events::add);
        List<Callable<Boolean>> shows = new ArrayList<>();
        for (int i = 0; i < 10; i++) { shows.add(() -> service.createShow(MOVIE.movieId(), show("NEW-SHOW", 2))); }
        assertEquals(1, ReservationConcurrencyTest.together(shows).stream().filter(Boolean::booleanValue).count());
        assertEquals(1, events.size());
        assertEquals(MOVIE.title(), service.getShowDetails("NEW-SHOW").orElseThrow().title());
    }

    @Test void disablingClearsHoldsAndWaitlistWithoutPromotingAndPreservesConfirmedBookings() {
        var service = new InMemoryReservationService();
        service.registerShow(show("SMALL", 2));
        var held = service.holdSeat("alice", "SMALL", "A1", 120000);
        var booked = service.book("bob", "SMALL", "A2");
        assertTrue(service.joinWaitlist("carol", "SMALL").success());
        List<ShowUpdatedEvent> events = new ArrayList<>();
        service.addCatalogChangeListener(events::add);
        assertTrue(service.disableShow("SMALL"));
        assertFalse(events.get(0).enabled());
        assertTrue(service.getActiveHolds("SMALL").isEmpty());
        assertEquals(0, service.status("carol", "SMALL").orElseThrow().totalWaitlistSize());
        assertFalse(service.confirmHold("alice", held.holdId(), "confirm").success());
        assertEquals(ErrorCode.SHOW_DISABLED, service.holdSeat("carol", "SMALL", "A1", 1000).errorCode());
        assertEquals(ErrorCode.SHOW_DISABLED, service.book("carol", "SMALL", "A1").errorCode());
        assertEquals(ErrorCode.SHOW_DISABLED, service.joinWaitlist("carol", "SMALL").errorCode());
        assertEquals(1, service.listBookings("SMALL", 0, 20).totalItems());
        assertTrue(service.cancel("bob", booked.bookingId()).success());
        assertTrue(service.getActiveHolds("SMALL").isEmpty());
        assertTrue(service.getShowDetails("SMALL").isPresent());
    }

    @Test void disableAndConfirmRaceNeverLeavesAConfirmedHoldOrNewAllocationAfterDisable() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
            var service = new InMemoryReservationService();
            var hold = service.holdSeat("alice", "SHOW-01", "A1", 120000);
            List<Callable<Object>> calls = List.of(() -> service.disableShow("SHOW-01"),
                () -> service.confirmHold("alice", hold.holdId(), "key"));
            var results = ReservationConcurrencyTest.together(calls);
            var confirmed = (BookingResult) results.get(1);
            assertEquals(confirmed.success() ? 1 : 0, service.listBookings("SHOW-01", 0, 20).totalItems());
            assertTrue(service.getActiveHolds("SHOW-01").isEmpty());
            assertEquals(ErrorCode.SHOW_DISABLED, service.book("bob", "SHOW-01", "A2").errorCode());
        }
    }

    @Test void overrideAndCustomerBookingRaceKeepOneReservationAndNoStaleIndex() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
            var service = new InMemoryReservationService();
            List<Callable<Boolean>> calls = List.of(() -> {
                try { return service.overrideSeat("admin", "SHOW-01", "A1", SeatStatus.BOOKED); }
                catch (AdminOperationException conflict) { assertEquals(ErrorCode.INVALID_STATE, conflict.errorCode()); return false; }
            }, () -> service.book("alice", "SHOW-01", "A1").success());
            assertEquals(1, ReservationConcurrencyTest.together(calls).stream().filter(Boolean::booleanValue).count());
            var old = service.listBookings("SHOW-01", 0, 20).items().get(0);
            assertEquals(1, service.listBookings("SHOW-01", 0, 20).totalItems());
            assertTrue(service.overrideSeat("admin", "SHOW-01", "A1", SeatStatus.AVAILABLE));
            assertEquals(0, service.listBookings("SHOW-01", 0, 20).totalItems());
            assertEquals(ErrorCode.BOOKING_NOT_FOUND, service.cancel(old.userId(), old.bookingId()).errorCode());
            assertTrue(service.book("bob", "SHOW-01", "A1").success());
        }
    }

    @Test void forcedHoldReleasePromotesFifoAndOverrideCannotSkipWaitingUsers() {
        var service = new InMemoryReservationService();
        service.registerShow(show("ONE", 1));
        var held = service.holdSeat("alice", "ONE", "A1", 120000);
        assertTrue(service.joinWaitlist("bob", "ONE").success());
        assertTrue(service.joinWaitlist("carol", "ONE").success());
        var rejection = assertThrows(AdminOperationException.class,
            () -> service.overrideSeat("admin", "ONE", "A1", SeatStatus.AVAILABLE));
        assertEquals(ErrorCode.INVALID_STATE, rejection.errorCode());
        assertTrue(service.forceCancelHold(held.holdId()));
        assertEquals("bob", service.getActiveHolds("ONE").get(0).heldByUserId());
        assertEquals(1, service.getQueuePosition("carol", "ONE"));
    }

    @Test void venueOverlapAndTooShortMovieWindowAreRejectedAtomically() {
        var service = new InMemoryReservationService();
        service.createMovie(MOVIE);
        assertTrue(service.createShow(MOVIE.movieId(), show("FIRST", 2)));
        assertEquals(ErrorCode.INVALID_STATE, assertThrows(AdminOperationException.class,
            () -> service.createShow(MOVIE.movieId(), show("OVERLAP", 2))).errorCode());
        var template = show("SHORT", 2);
        var tooShort = new ShowDetails(template.showId(), template.title(), template.description(), template.genre(),
            template.startTime(), "2030-01-01T18:30:00Z", "Different Room", template.layout());
        assertThrows(AdminOperationException.class, () -> service.createShow(MOVIE.movieId(), tooShort));
        assertTrue(service.getShowDetails("OVERLAP").isEmpty());
        assertTrue(service.getShowDetails("SHORT").isEmpty());
    }
}
