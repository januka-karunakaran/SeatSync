package com.seatsync.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.seatsync.common.model.*;
import com.seatsync.common.protocol.Message;
import com.seatsync.common.protocol.ProtocolJson;
import com.seatsync.common.protocol.request.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExtendedAdminFlowTest {
    @BeforeAll static void accounts() { AdminSecurityTest.accounts(); }
    private static final MovieDetails MOVIE = new MovieDetails("MOVIE-NEW", "Canonical title", "Description", "Drama", 90);
    private static JsonNode send(AdminSecurityTest.Harness h, String actor, Message request) {
        var session = new AdminSecurityTest.Session(actor);
        h.dispatcher.dispatch(session, ProtocolJson.serialize(request));
        return session.last();
    }

    @Test void adminCreatesMovieAndLinkedShowThenDisablesItAndReadsBookings() {
        var h = new AdminSecurityTest.Harness();
        var createMovie = new AdminCreateMovieRequest("movie", MOVIE);
        assertEquals("ADMIN_MOVIE_CREATED", send(h, "demo-admin", createMovie).path("type").asText());
        assertEquals("MOVIE_ALREADY_EXISTS", send(h, "demo-admin", createMovie).path("errorCode").asText());
        var createShow = new AdminCreateShowRequest("show", MOVIE.movieId(), AdminSecurityTest.show("SHOW-NEW"));
        assertEquals("ADMIN_SHOW_ADDED", send(h, "demo-admin", createShow).path("type").asText());
        assertEquals(MOVIE.title(), h.catalog.getShowDetails("SHOW-NEW").orElseThrow().title());
        assertEquals("SHOW_ALREADY_EXISTS", send(h, "demo-admin", createShow).path("errorCode").asText());
        var booking = h.catalog.book("demo-alice", "SHOW-NEW", "A1");
        assertTrue(booking.success());
        var page = send(h, "demo-admin", new AdminListBookingsRequest("bookings", "SHOW-NEW", 0, 1));
        assertEquals("ADMIN_BOOKINGS_REPLY", page.path("type").asText());
        assertEquals("demo-alice", page.path("bookings").path("items").get(0).path("userId").asText());
        var disable = new AdminDisableShowRequest("disable", "SHOW-NEW");
        assertEquals("ADMIN_SHOW_DISABLED", send(h, "demo-admin", disable).path("type").asText());
        assertEquals(ErrorCode.SHOW_DISABLED, h.catalog.book("demo-bob", "SHOW-NEW", "A2").errorCode());
        assertFalse(h.catalog.getShows().stream().anyMatch(show -> show.showId().equals("SHOW-NEW")));
        assertEquals(1, h.catalog.listBookings("SHOW-NEW", 0, 20).totalItems());
        assertTrue(h.catalog.cancel("demo-alice", booking.bookingId()).success());
        assertEquals("INVALID_STATE", send(h, "demo-admin", disable).path("errorCode").asText());
    }

    @Test void eachExtendedAdminCommandDeniesWrongRoleAndMissingIdentity() {
        var h = new AdminSecurityTest.Harness();
        List<Message> requests = List.of(new AdminCreateMovieRequest("movie", MOVIE),
            new AdminCreateShowRequest("show", MOVIE.movieId(), AdminSecurityTest.show("SHOW-NEW")),
            new AdminDisableShowRequest("disable", "SHOW-01"), new AdminListBookingsRequest("list", "SHOW-01", 0, 10));
        for (Message request : requests) {
            assertEquals("FORBIDDEN_ROLE", send(h, "demo-alice", request).path("errorCode").asText());
            assertEquals("AUTH_REQUIRED", send(h, null, request).path("errorCode").asText());
        }
        assertFalse(h.catalog.getMovies().contains(MOVIE));
        assertEquals(6, h.catalog.getShows().size());
    }

    @Test void malformedMovieUnknownMovieAndInvalidPaginationReturnStructuredErrors() {
        var h = new AdminSecurityTest.Harness();
        assertEquals("INVALID_REQUEST_FORMAT", h.send(new AdminSecurityTest.Session("demo-admin"),
            Map.of("type", "ADMIN_CREATE_MOVIE", "requestId", "invalid", "movie",
                Map.of("movieId", "M", "title", "Bad", "description", "desc", "genre", "drama", "durationMinutes", -1)))
            .path("errorCode").asText());
        assertEquals("MOVIE_NOT_FOUND", send(h, "demo-admin", new AdminCreateShowRequest("unknown", "MISSING",
            AdminSecurityTest.show("SHOW-NEW"))).path("errorCode").asText());
        assertEquals("SHOW_NOT_FOUND", send(h, "demo-admin", new AdminDisableShowRequest("disable", "MISSING"))
            .path("errorCode").asText());
        assertEquals("INVALID_PAGE_SIZE", send(h, "demo-admin", new AdminListBookingsRequest("list", "SHOW-01", -1, 20))
            .path("errorCode").asText());
        assertEquals("INVALID_PAGE_SIZE", send(h, "demo-admin", new AdminListBookingsRequest("list", "SHOW-01", 0, 21))
            .path("errorCode").asText());
    }

    @Test void customerMovieBrowsingIsPagedButAdministrativeOwnersStayPrivate() {
        var h = new AdminSecurityTest.Harness();
        var first = send(h, "demo-alice", new GetMoviesRequest("browse", 0, 1));
        assertEquals("MOVIE_LIST_REPLY", first.path("type").asText());
        assertEquals(1, first.path("movies").path("items").size());
        assertTrue(first.path("movies").path("hasMore").asBoolean());
        assertFalse(first.toString().contains("userId"));
        assertEquals("AUTH_REQUIRED", send(h, null, new GetMoviesRequest("anonymous", 0, 1)).path("errorCode").asText());
        assertEquals("INVALID_PAGE_SIZE", send(h, "demo-alice", new GetMoviesRequest("bad", 0, 6)).path("errorCode").asText());
        assertEquals(0, send(h, "demo-alice", new GetMoviesRequest("last", Integer.MAX_VALUE, 5))
            .path("movies").path("items").size());
    }

    @Test void actualSeatOverridesMaintainBookingAndHoldIndexes() {
        var h = new AdminSecurityTest.Harness();
        var book = h.catalog.book("demo-alice", "SHOW-01", "A1");
        assertTrue(book.success());
        assertEquals("ADMIN_SEAT_UPDATED", send(h, "demo-admin", new AdminUpdateSeatStatusRequest("free", "SHOW-01", "A1",
            SeatStatus.AVAILABLE)).path("type").asText());
        assertEquals(ErrorCode.BOOKING_NOT_FOUND, h.catalog.cancel("demo-alice", book.bookingId()).errorCode());
        assertEquals("ADMIN_SEAT_UPDATED", send(h, "demo-admin", new AdminUpdateSeatStatusRequest("hold", "SHOW-01", "A1",
            SeatStatus.HELD)).path("type").asText());
        var hold = h.catalog.getActiveHolds("SHOW-01").get(0);
        assertEquals("demo-admin", hold.heldByUserId());
        assertEquals("ADMIN_HOLD_CANCELLED", send(h, "demo-admin", new AdminCancelHoldRequest("release", hold.holdId(),
            "Administrative release")).path("type").asText());
        assertTrue(h.catalog.getActiveHolds("SHOW-01").isEmpty());
        assertEquals("ADMIN_SEAT_UPDATED", send(h, "demo-admin", new AdminUpdateSeatStatusRequest("allocate", "SHOW-01", "A1",
            SeatStatus.BOOKED)).path("type").asText());
        assertEquals("demo-admin", h.catalog.listBookings("SHOW-01", 0, 20).items().get(0).userId());
        assertEquals("INVALID_STATE", send(h, "demo-admin", new AdminUpdateSeatStatusRequest("conflict", "SHOW-01", "A1",
            SeatStatus.HELD)).path("errorCode").asText());
    }
}
