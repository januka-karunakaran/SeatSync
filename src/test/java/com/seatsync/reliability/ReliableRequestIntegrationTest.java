package com.seatsync.reliability;

import com.fasterxml.jackson.databind.JsonNode;
import com.seatsync.booking.InMemoryReservationService;
import com.seatsync.common.contracts.AuthService;
import com.seatsync.common.contracts.RequestContext;
import com.seatsync.common.model.AuthResult;
import com.seatsync.common.protocol.ProtocolJson;
import com.seatsync.concurrency.BookingRequestProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReliableRequestIntegrationTest (U07)")
class ReliableRequestIntegrationTest {

    private InMemoryReservationService reservations;
    private IdempotencyCache cache;
    private IdempotentRequestDispatcher dispatcher;

    private static class TestContext implements RequestContext {
        final String sessionId;
        final String userId;
        final List<String> replies = new CopyOnWriteArrayList<>();
        final AtomicBoolean open = new AtomicBoolean(true);

        TestContext(String sessionId, String userId) {
            this.sessionId = sessionId;
            this.userId = userId;
        }

        @Override public String getSessionId() { return sessionId; }
        @Override public Optional<String> getAuthenticatedUserId() { return Optional.ofNullable(userId); }
        @Override public void setAuthenticatedUserId(String u) {}
        @Override public void clearAuthentication() {}
        @Override public boolean isOpen() { return open.get(); }
        @Override public void close() { open.set(false); }
        @Override public void send(String line) {
            if (open.get() && line != null) {
                replies.add(line);
            }
        }

        JsonNode lastReply() {
            assertFalse(replies.isEmpty(), "No replies sent to context");
            return ProtocolJson.parseTree(replies.get(replies.size() - 1));
        }
    }

    @BeforeEach
    void setUp() {
        reservations = new InMemoryReservationService();
        cache = new IdempotencyCache();
        AuthService mockAuth = (user, pass) -> AuthResult.ok("USR-ALICE", user, "ok");
        BookingRequestProcessor bookingProcessor = new BookingRequestProcessor(mockAuth, reservations);
        dispatcher = new IdempotentRequestDispatcher(bookingProcessor, cache);
    }

    @Test
    @DisplayName("Retrying a booking with the same requestId returns original BOOK_OK without double booking")
    void testRetryingBookingReturnsOriginalBookOk() {
        TestContext context = new TestContext("sess-1", "USR-ALICE");
        String bookRequest = "{\"type\":\"BOOK_SEAT\",\"requestId\":\"req-book-001\",\"showId\":\"SHOW-01\",\"seatId\":\"A1\"}";

        // 1. First execution
        dispatcher.dispatch(context, bookRequest);
        JsonNode reply1 = context.lastReply();
        assertEquals("BOOK_OK", reply1.path("type").asText());
        String bookingId1 = reply1.path("bookingId").asText();
        assertNotNull(bookingId1);

        // 2. Simulated packet drop & client retry: re-transmit identical request with SAME requestId
        TestContext retryContext = new TestContext("sess-2", "USR-ALICE");
        dispatcher.dispatch(retryContext, bookRequest);
        JsonNode reply2 = retryContext.lastReply();

        // Must return identical BOOK_OK and same bookingId (NOT SEAT_ALREADY_BOOKED!)
        assertEquals("BOOK_OK", reply2.path("type").asText());
        assertEquals(bookingId1, reply2.path("bookingId").asText());

        // 3. Ensure no second booking or conflicting seat state was created
        var snapshot = reservations.snapshot("SHOW-01");
        long bookedCountA1 = snapshot.seats().stream()
            .filter(s -> "A1".equals(s.seatId()) && s.status() == com.seatsync.common.model.SeatStatus.BOOKED)
            .count();
        assertEquals(1, bookedCountA1, "Exactly one booking must exist for seat A1");
    }

    @Test
    @DisplayName("Reusing the same requestId with a different payload is rejected as CONFLICT")
    void testConflictingPayloadReusingRequestIdIsRejected() {
        TestContext context = new TestContext("sess-1", "USR-ALICE");
        String request1 = "{\"type\":\"BOOK_SEAT\",\"requestId\":\"req-fixed-999\",\"showId\":\"SHOW-01\",\"seatId\":\"B1\"}";
        dispatcher.dispatch(context, request1);
        assertEquals("BOOK_OK", context.lastReply().path("type").asText());

        // Reusing same requestId for seat B2
        TestContext context2 = new TestContext("sess-1", "USR-ALICE");
        String request2 = "{\"type\":\"BOOK_SEAT\",\"requestId\":\"req-fixed-999\",\"showId\":\"SHOW-01\",\"seatId\":\"B2\"}";
        dispatcher.dispatch(context2, request2);

        JsonNode reply2 = context2.lastReply();
        assertEquals("ERROR", reply2.path("type").asText());
        assertEquals("INVALID_REQUEST_FORMAT", reply2.path("errorCode").asText());
        assertTrue(reply2.path("message").asText().contains("Conflicting payload"));
    }
}
