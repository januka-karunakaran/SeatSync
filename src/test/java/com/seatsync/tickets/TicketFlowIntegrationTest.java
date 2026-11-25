package com.seatsync.tickets;

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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TicketFlowIntegrationTest (U09)")
class TicketFlowIntegrationTest {

    private InMemoryReservationService reservations;
    private TicketManager ticketManager;
    private BookingRequestProcessor processor;

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
        ticketManager = new TicketManager();
        AuthService mockAuth = (user, pass) -> AuthResult.ok("USR-ALICE", user, "ok");
        processor = new BookingRequestProcessor(mockAuth, reservations, null, ticketManager, ticketManager);
    }

    @Test
    @DisplayName("End-to-end flow: Book -> View History -> Get Ticket -> Cancel -> Invalidate Ticket")
    void testEndToEndBookingTicketAndCancellationFlow() {
        TestContext aliceContext = new TestContext("sess-1", "USR-ALICE");

        // 1. Book seat A1
        String bookReq = "{\"type\":\"BOOK_SEAT\",\"requestId\":\"req-1\",\"showId\":\"SHOW-01\",\"seatId\":\"A1\"}";
        processor.dispatch(aliceContext, bookReq);
        JsonNode bookReply = aliceContext.lastReply();
        assertEquals("BOOK_OK", bookReply.path("type").asText());
        String bookingId = bookReply.path("bookingId").asText();

        // 2. Query booking history
        String historyReq = "{\"type\":\"GET_BOOKING_HISTORY\",\"requestId\":\"req-2\",\"pageNumber\":0,\"pageSize\":10}";
        processor.dispatch(aliceContext, historyReq);
        JsonNode historyReply = aliceContext.lastReply();
        assertEquals("BOOKING_HISTORY_REPLY", historyReply.path("type").asText());
        JsonNode items = historyReply.path("history").path("items");
        assertEquals(1, items.size());
        assertEquals(bookingId, items.get(0).path("bookingId").asText());
        String ticketId = items.get(0).path("ticketId").asText();
        assertNotNull(ticketId);

        // 3. Query digital ticket details
        String ticketReq = "{\"type\":\"GET_TICKET\",\"requestId\":\"req-3\",\"ticketId\":\"" + ticketId + "\"}";
        processor.dispatch(aliceContext, ticketReq);
        JsonNode ticketReply = aliceContext.lastReply();
        assertEquals("TICKET_REPLY", ticketReply.path("type").asText());
        assertEquals(ticketId, ticketReply.path("ticket").path("ticketId").asText());
        assertEquals("USR-ALICE", ticketReply.path("ticket").path("userId").asText());
        String hash = ticketReply.path("ticket").path("verificationHash").asText();

        String verifyReq = "{\"type\":\"VERIFY_TICKET\",\"requestId\":\"req-verify\",\"ticketId\":\""
            + ticketId + "\",\"verificationHash\":\"" + hash + "\"}";
        processor.dispatch(aliceContext, verifyReq);
        JsonNode verified = aliceContext.lastReply();
        assertEquals("TICKET_VERIFIED", verified.path("type").asText());
        assertTrue(verified.path("valid").asBoolean());

        String badHash = "{\"type\":\"VERIFY_TICKET\",\"requestId\":\"req-verify-bad\",\"ticketId\":\""
            + ticketId + "\",\"verificationHash\":\"0000\"}";
        processor.dispatch(aliceContext, badHash);
        assertFalse(aliceContext.lastReply().path("valid").asBoolean());

        TestContext stranger = new TestContext("sess-2", "USR-BOB");
        processor.dispatch(stranger, verifyReq);
        assertEquals("TICKET_NOT_FOUND", stranger.lastReply().path("errorCode").asText());

        // 4. Cancel booking
        String cancelReq = "{\"type\":\"CANCEL_BOOKING\",\"requestId\":\"req-4\",\"bookingId\":\"" + bookingId + "\"}";
        processor.dispatch(aliceContext, cancelReq);
        JsonNode cancelReply = aliceContext.lastReply();
        assertEquals("CANCEL_OK", cancelReply.path("type").asText());

        // 5. Query ticket again -> should be invalidated (TICKET_NOT_FOUND)
        String ticketReqAfter = "{\"type\":\"GET_TICKET\",\"requestId\":\"req-5\",\"ticketId\":\"" + ticketId + "\"}";
        processor.dispatch(aliceContext, ticketReqAfter);
        JsonNode ticketReplyAfter = aliceContext.lastReply();
        assertEquals("ERROR", ticketReplyAfter.path("type").asText());
        assertEquals("TICKET_NOT_FOUND", ticketReplyAfter.path("errorCode").asText());

        // 6. Query history again -> status CANCELLED
        processor.dispatch(aliceContext, historyReq);
        JsonNode historyReplyAfter = aliceContext.lastReply();
        assertEquals("CANCELLED", historyReplyAfter.path("history").path("items").get(0).path("status").asText());
    }
}
