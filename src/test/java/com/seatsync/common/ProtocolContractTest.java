package com.seatsync.common;

import com.seatsync.common.contracts.RequestContext;
import com.seatsync.common.model.AuthResult;
import com.seatsync.common.model.BookingResult;
import com.seatsync.common.model.CancelResult;
import com.seatsync.common.model.ErrorCode;
import com.seatsync.common.model.ProtocolConstants;
import com.seatsync.common.model.SeatSnapshot;
import com.seatsync.common.model.SeatStatus;
import com.seatsync.common.model.SeatView;
import com.seatsync.common.protocol.Message;
import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.ProtocolJson;
import com.seatsync.common.protocol.event.SeatUpdatedEvent;
import com.seatsync.common.protocol.reply.BookOkReply;
import com.seatsync.common.protocol.reply.BookRejectedReply;
import com.seatsync.common.protocol.reply.CancelOkReply;
import com.seatsync.common.protocol.reply.CancelRejectedReply;
import com.seatsync.common.protocol.reply.ErrorReply;
import com.seatsync.common.protocol.reply.LoginFailedReply;
import com.seatsync.common.protocol.reply.LoginOkReply;
import com.seatsync.common.protocol.reply.LogoutOkReply;
import com.seatsync.common.protocol.reply.SeatSnapshotReply;
import com.seatsync.common.protocol.request.BookSeatRequest;
import com.seatsync.common.protocol.request.CancelBookingRequest;
import com.seatsync.common.protocol.request.GetSeatsRequest;
import com.seatsync.common.protocol.request.LoginRequest;
import com.seatsync.common.protocol.request.LogoutRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies core SeatSync DTO immutability, protocol serialization rules,
 * wire framing limits, and contract invariants.
 */
class ProtocolContractTest {

    @Test
    void serializedMessagesHaveOneTypeFieldAcceptedByStrictWorkerParser() throws Exception {
        var strictReader = ProtocolJson.mapper().readerFor(com.fasterxml.jackson.databind.JsonNode.class)
            .with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
        List<Message> messages = List.of(
            new LoginRequest("login", "alice", "test"),
            new GetSeatsRequest("seats", "SHOW-01"),
            new BookSeatRequest("book", "SHOW-01", "A1"),
            new CancelBookingRequest("cancel", "BKG-test"),
            new LogoutRequest("logout"),
            new LoginOkReply("login", "id-alice", "alice"),
            new SeatUpdatedEvent("SHOW-01", 1, List.of(SeatView.available("A1")))
        );
        for (Message message : messages) {
            String json = ProtocolJson.serialize(message);
            com.fasterxml.jackson.databind.JsonNode frame = strictReader.readValue(json);
            assertEquals(message.getType().name(), frame.path("type").asText());
            assertEquals(message.getClass(), ProtocolJson.deserializeMessage(json).getClass());
        }
    }

    @Test
    @DisplayName("Test 1: Requests serialize and deserialize preserving requestId and omitting untrusted userId")
    void testClientRequestContracts() {
        String reqId = UUID.randomUUID().toString();

        // 1. LOGIN
        LoginRequest login = new LoginRequest(reqId, "alice", "secret123");
        String loginJson = ProtocolJson.serialize(login);
        Message parsedLogin = ProtocolJson.deserializeMessage(loginJson);
        assertInstanceOf(LoginRequest.class, parsedLogin);
        LoginRequest deserializedLogin = (LoginRequest) parsedLogin;
        assertEquals(reqId, deserializedLogin.getRequestId());
        assertEquals("alice", deserializedLogin.username());
        assertEquals("secret123", deserializedLogin.password());
        assertEquals(MessageType.LOGIN, deserializedLogin.getType());

        // 2. GET_SEATS
        GetSeatsRequest getSeats = new GetSeatsRequest(reqId, ProtocolConstants.DEFAULT_SHOW_ID);
        String getSeatsJson = ProtocolJson.serialize(getSeats);
        GetSeatsRequest deserializedGetSeats = (GetSeatsRequest) ProtocolJson.deserializeMessage(getSeatsJson);
        assertEquals(ProtocolConstants.DEFAULT_SHOW_ID, deserializedGetSeats.showId());

        // 3. BOOK_SEAT (must NOT carry client userId)
        BookSeatRequest bookSeat = new BookSeatRequest(reqId, ProtocolConstants.DEFAULT_SHOW_ID, "C4");
        String bookJson = ProtocolJson.serialize(bookSeat);
        assertFalse(bookJson.contains("userId"), "Client book request must not transmit or trust userId");
        BookSeatRequest deserializedBook = (BookSeatRequest) ProtocolJson.deserializeMessage(bookJson);
        assertEquals("C4", deserializedBook.seatId());

        // 4. CANCEL_BOOKING (must NOT carry client userId)
        CancelBookingRequest cancel = new CancelBookingRequest(reqId, "BKG-9942");
        String cancelJson = ProtocolJson.serialize(cancel);
        assertFalse(cancelJson.contains("userId"), "Client cancel request must not transmit or trust userId");
        CancelBookingRequest deserializedCancel = (CancelBookingRequest) ProtocolJson.deserializeMessage(cancelJson);
        assertEquals("BKG-9942", deserializedCancel.bookingId());

        // 5. LOGOUT
        LogoutRequest logout = new LogoutRequest(reqId);
        String logoutJson = ProtocolJson.serialize(logout);
        LogoutRequest deserializedLogout = (LogoutRequest) ProtocolJson.deserializeMessage(logoutJson);
        assertEquals(MessageType.LOGOUT, deserializedLogout.getType());
    }

    @Test
    @DisplayName("Test 2: Server replies and asynchronous events serialize correctly")
    void testServerRepliesAndEvents() {
        String reqId = "req-12345";

        // LOGIN_OK
        LoginOkReply loginOk = new LoginOkReply(reqId, "USR-01", "alice");
        String loginOkJson = ProtocolJson.serialize(loginOk);
        LoginOkReply parsedLoginOk = (LoginOkReply) ProtocolJson.deserializeMessage(loginOkJson);
        assertEquals("USR-01", parsedLoginOk.userId());

        // LOGIN_FAILED
        LoginFailedReply loginFailed = new LoginFailedReply(reqId, ErrorCode.INVALID_CREDENTIALS, "Bad password");
        String loginFailedJson = ProtocolJson.serialize(loginFailed);
        LoginFailedReply parsedLoginFailed = (LoginFailedReply) ProtocolJson.deserializeMessage(loginFailedJson);
        assertEquals(ErrorCode.INVALID_CREDENTIALS, parsedLoginFailed.errorCode());

        // SEAT_SNAPSHOT
        List<SeatView> seats = List.of(
            SeatView.available("A1"),
            SeatView.booked("A2", "user-bob")
        );
        SeatSnapshotReply snapshotReply = new SeatSnapshotReply(reqId, "SHOW-01", 10L, seats);
        String snapshotJson = ProtocolJson.serialize(snapshotReply);
        SeatSnapshotReply parsedSnapshot = (SeatSnapshotReply) ProtocolJson.deserializeMessage(snapshotJson);
        assertEquals(2, parsedSnapshot.seats().size());
        assertEquals("A1", parsedSnapshot.seats().get(0).seatId());
        assertEquals(SeatStatus.AVAILABLE, parsedSnapshot.seats().get(0).status());
        assertEquals(SeatStatus.BOOKED, parsedSnapshot.seats().get(1).status());

        // BOOK_OK
        BookOkReply bookOk = new BookOkReply(reqId, "BKG-1", "SHOW-01", "A1", "Booking confirmed");
        BookOkReply parsedBookOk = (BookOkReply) ProtocolJson.deserializeMessage(ProtocolJson.serialize(bookOk));
        assertEquals("BKG-1", parsedBookOk.bookingId());

        // BOOK_REJECTED
        BookRejectedReply bookRejected = new BookRejectedReply(reqId, ErrorCode.SEAT_ALREADY_BOOKED, "Seat taken");
        BookRejectedReply parsedBookRejected = (BookRejectedReply) ProtocolJson.deserializeMessage(ProtocolJson.serialize(bookRejected));
        assertEquals(ErrorCode.SEAT_ALREADY_BOOKED, parsedBookRejected.errorCode());

        // CANCEL_OK
        CancelOkReply cancelOk = new CancelOkReply(reqId, "BKG-1", "A1", "Cancelled");
        CancelOkReply parsedCancelOk = (CancelOkReply) ProtocolJson.deserializeMessage(ProtocolJson.serialize(cancelOk));
        assertEquals("BKG-1", parsedCancelOk.bookingId());

        // CANCEL_REJECTED
        CancelRejectedReply cancelRejected = new CancelRejectedReply(reqId, ErrorCode.NOT_BOOKING_OWNER, "Not owner");
        CancelRejectedReply parsedCancelRejected = (CancelRejectedReply) ProtocolJson.deserializeMessage(ProtocolJson.serialize(cancelRejected));
        assertEquals(ErrorCode.NOT_BOOKING_OWNER, parsedCancelRejected.errorCode());

        // LOGOUT_OK
        LogoutOkReply logoutOk = new LogoutOkReply(reqId, "Logged out");
        LogoutOkReply parsedLogoutOk = (LogoutOkReply) ProtocolJson.deserializeMessage(ProtocolJson.serialize(logoutOk));
        assertEquals("Logged out", parsedLogoutOk.message());

        // ERROR
        ErrorReply error = new ErrorReply(reqId, ErrorCode.INTERNAL_ERROR, "Server failure");
        ErrorReply parsedError = (ErrorReply) ProtocolJson.deserializeMessage(ProtocolJson.serialize(error));
        assertEquals(ErrorCode.INTERNAL_ERROR, parsedError.errorCode());

        // SEAT_UPDATED event (does NOT have requestId)
        SeatUpdatedEvent event = new SeatUpdatedEvent("SHOW-01", 11L, List.of(SeatView.booked("A1", "alice")));
        String eventJson = ProtocolJson.serialize(event);
        assertFalse(eventJson.contains("requestId"), "Server events do not carry client requestId");
        SeatUpdatedEvent parsedEvent = (SeatUpdatedEvent) ProtocolJson.deserializeMessage(eventJson);
        assertEquals(11L, parsedEvent.version());
        assertEquals(1, parsedEvent.seats().size());
        assertEquals("A1", parsedEvent.seats().get(0).seatId());
    }

    @Test
    @DisplayName("Test 3: Enforces 16 KiB frame limit and wire framing")
    void testFrameLimits() {
        // Frame within 16 KiB passes
        String validJson = "{\"type\":\"LOGOUT\",\"requestId\":\"123\"}";
        assertDoesNotThrow(() -> ProtocolJson.deserializeMessage(validJson));

        // Wire formatting appends newline
        String wireLine = ProtocolJson.serializeToWire(new LogoutRequest("123"));
        assertTrue(wireLine.endsWith("\n"), "Wire frame must be terminated with newline");

        // Oversized payload (> 16 KiB = 16384 bytes) must be rejected
        StringBuilder oversized = new StringBuilder("{\"type\":\"LOGIN\",\"requestId\":\"123\",\"username\":\"test\",\"password\":\"");
        while (oversized.length() < ProtocolConstants.MAX_FRAME_BYTES + 50) {
            oversized.append("A");
        }
        oversized.append("\"}");

        assertThrows(IllegalArgumentException.class, () -> {
            ProtocolJson.deserialize(oversized.toString(), Message.class);
        }, "Frames exceeding 16 KiB must throw IllegalArgumentException");
    }

    @Test
    @DisplayName("Test 4: Domain records guarantee immutability and validate seat IDs A1-E8")
    void testDomainRecordsAndSeatInvariants() {
        // Validate Seat IDs: A1 through E8 = 40 seats
        assertEquals(40, ProtocolConstants.VALID_SEAT_IDS.size());
        assertTrue(ProtocolConstants.isValidSeatId("A1"));
        assertTrue(ProtocolConstants.isValidSeatId("E8"));
        assertTrue(ProtocolConstants.isValidSeatId("c4")); // case-insensitive check
        assertFalse(ProtocolConstants.isValidSeatId("A9"));
        assertFalse(ProtocolConstants.isValidSeatId("F1"));
        assertFalse(ProtocolConstants.isValidSeatId(null));
        assertFalse(ProtocolConstants.isValidSeatId(""));

        // Snapshot defensive copying
        List<SeatView> mutableList = new ArrayList<>();
        mutableList.add(SeatView.available("A1"));
        SeatSnapshot snapshot = new SeatSnapshot("SHOW-01", 1L, mutableList);

        // Modifying original list must not affect snapshot
        mutableList.add(SeatView.available("A2"));
        assertEquals(1, snapshot.seats().size());

        // Snapshot list cannot be mutated directly
        assertThrows(UnsupportedOperationException.class, () -> {
            snapshot.seats().add(SeatView.available("A3"));
        });

        // BookingResult factory methods
        BookingResult ok = BookingResult.ok("B-1", "SHOW-01", "A1", "Success");
        assertTrue(ok.success());
        assertNull(ok.errorCode());

        BookingResult rejected = BookingResult.rejected("SHOW-01", "A1", ErrorCode.SEAT_ALREADY_BOOKED, "Taken");
        assertFalse(rejected.success());
        assertEquals(ErrorCode.SEAT_ALREADY_BOOKED, rejected.errorCode());

        // CancelResult factory methods
        CancelResult cancelOk = CancelResult.ok("B-1", "A1", "Cancelled");
        assertTrue(cancelOk.success());

        CancelResult cancelRejected = CancelResult.rejected("B-1", ErrorCode.NOT_BOOKING_OWNER, "Forbidden");
        assertFalse(cancelRejected.success());

        // AuthResult factory methods
        AuthResult authOk = AuthResult.ok("U-1", "bob", "Welcome");
        assertTrue(authOk.success());

        AuthResult authFail = AuthResult.failed(ErrorCode.INVALID_CREDENTIALS, "Bad credentials");
        assertFalse(authFail.success());
    }

    @Test
    @DisplayName("Test 5: RequestContext session abstraction verifies user identity and thread-safe send contract")
    void testRequestContextSessionContract() {
        AtomicReference<String> outbound = new AtomicReference<>();
        AtomicReference<String> boundUser = new AtomicReference<>();

        RequestContext mockContext = new RequestContext() {
            @Override
            public String getSessionId() {
                return "session-test-01";
            }

            @Override
            public Optional<String> getAuthenticatedUserId() {
                return Optional.ofNullable(boundUser.get());
            }

            @Override
            public void setAuthenticatedUserId(String userId) {
                boundUser.set(userId);
            }

            @Override
            public void clearAuthentication() {
                boundUser.set(null);
            }

            @Override
            public void send(String jsonLine) {
                outbound.set(jsonLine);
            }

            @Override
            public boolean isOpen() {
                return true;
            }

            @Override
            public void close() {
                boundUser.set(null);
            }
        };

        // Initially unauthenticated
        assertFalse(mockContext.isAuthenticated());
        assertTrue(mockContext.getAuthenticatedUserId().isEmpty());

        // Authenticate session
        mockContext.setAuthenticatedUserId("USR-ALICE-100");
        assertTrue(mockContext.isAuthenticated());
        assertEquals("USR-ALICE-100", mockContext.getAuthenticatedUserId().get());

        // Outbound send API
        mockContext.send("{\"type\":\"LOGOUT_OK\",\"requestId\":\"1\"}");
        assertEquals("{\"type\":\"LOGOUT_OK\",\"requestId\":\"1\"}", outbound.get());

        // Logout
        mockContext.clearAuthentication();
        assertFalse(mockContext.isAuthenticated());
    }
}
