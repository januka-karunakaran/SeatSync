package com.seatsync.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.seatsync.common.contracts.RequestContext;
import com.seatsync.common.model.ErrorCode;
import com.seatsync.common.protocol.ProtocolJson;
import com.seatsync.server.MockRequestDispatcher;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SecurityTest {
    private static PasswordHasher hasher;
    private static DemoUserRepository users;
    private static AuthService auth;
    private static char[] demoPassword;

    @BeforeAll
    static void setUp() {
        byte[] random = new byte[24];
        new SecureRandom().nextBytes(random);
        demoPassword = Base64.getEncoder().encodeToString(random).toCharArray();
        hasher = new PasswordHasher();
        char[] input = demoPassword.clone();
        users = new DemoUserRepository(input, hasher);
        assertTrue(allCleared(input));
        auth = new AuthService(users, hasher);
    }

    @Test
    void hashesUseDistinctSaltsAndCredentialsAreWiped() {
        User alice = users.findByUsername("alice").orElseThrow();
        User bob = users.findByUsername("bob").orElseThrow();
        assertFalse(Arrays.equals(alice.salt(), bob.salt()));
        assertFalse(Arrays.equals(alice.hash(), bob.hash()));
        char[] input = demoPassword.clone();
        var result = auth.login("alice", input);
        assertTrue(result.success());
        assertEquals("demo-alice", result.userId());
        assertTrue(allCleared(input));
        byte[] salt = alice.salt();
        salt[0] ^= 1;
        assertFalse(Arrays.equals(salt, alice.salt()));
    }

    @Test
    void rejectsWrongUnknownAndEmptyCredentials() {
        char[] wrong = {'n', 'o'};
        assertEquals(ErrorCode.INVALID_CREDENTIALS, auth.login("alice", wrong).errorCode());
        assertTrue(allCleared(wrong));
        assertFalse(auth.login("unknown", demoPassword.clone()).success());
        assertFalse(auth.login("", demoPassword.clone()).success());
        assertFalse(auth.login("alice", new char[0]).success());
        assertFalse(auth.login(null, null).success());
    }

    @Test
    void gateRequiresLoginAndCancellationRequiresOwner() {
        var dispatcher = new SecurityRequestDispatcher(auth, new AuthorizationService(), new MockRequestDispatcher());
        var alice = new Session();
        var bob = new Session();
        send(dispatcher, alice, "{\"type\":\"BOOK_SEAT\",\"requestId\":\"1\",\"showId\":\"SHOW-01\",\"seatId\":\"A1\"}");
        assertEquals("AUTH_REQUIRED", alice.last().path("errorCode").asText());
        send(dispatcher, alice, login("alice", "wrong"));
        assertEquals("LOGIN_FAILED", alice.last().path("type").asText());
        send(dispatcher, alice, login("alice", new String(demoPassword)));
        assertEquals("LOGIN_OK", alice.last().path("type").asText());
        send(dispatcher, alice, "{\"type\":\"BOOK_SEAT\",\"requestId\":\"2\",\"showId\":\"SHOW-01\",\"seatId\":\"A1\"}");
        assertEquals("BOOK_OK", alice.last().path("type").asText());
        String booking = alice.last().path("bookingId").asText();
        send(dispatcher, bob, login("bob", new String(demoPassword)));
        send(dispatcher, bob, "{\"type\":\"CANCEL_BOOKING\",\"requestId\":\"3\",\"bookingId\":\"" + booking + "\"}");
        assertEquals("NOT_BOOKING_OWNER", bob.last().path("errorCode").asText());
        send(dispatcher, alice, "{\"type\":\"CANCEL_BOOKING\",\"requestId\":\"4\",\"bookingId\":\"" + booking + "\"}");
        assertEquals("CANCEL_OK", alice.last().path("type").asText());
    }

    @Test
    void malformedAndForgedRequestsAreRejected() {
        var dispatcher = new SecurityRequestDispatcher(auth, new AuthorizationService(), new MockRequestDispatcher());
        var session = new Session();
        send(dispatcher, session, "{\"type\":\"GET_SEATS\",\"requestId\":\"1\",\"showId\":\"SHOW-01\"}");
        assertEquals("AUTH_REQUIRED", session.last().path("errorCode").asText());
        send(dispatcher, session, "{\"type\":\"CANCEL_BOOKING\",\"requestId\":\"1c\",\"bookingId\":\"BKG-1\"}");
        assertEquals("AUTH_REQUIRED", session.last().path("errorCode").asText());
        send(dispatcher, session, "{broken");
        assertEquals("INVALID_REQUEST_FORMAT", session.last().path("errorCode").asText());
        send(dispatcher, session, "{\"type\":\"LOGIN\",\"type\":\"LOGOUT\",\"requestId\":\"dup\"}");
        assertEquals("INVALID_REQUEST_FORMAT", session.last().path("errorCode").asText());
        send(dispatcher, session, login("alice", new String(demoPassword)));
        send(dispatcher, session, "{\"type\":\"BOOK_SEAT\",\"requestId\":\"2\",\"showId\":\"SHOW-01\",\"seatId\":\"A1\",\"userId\":\"demo-bob\"}");
        assertEquals("INVALID_REQUEST_FORMAT", session.last().path("errorCode").asText());
        send(dispatcher, session, "{\"type\":\"BOOK_SEAT\",\"requestId\":\"3\",\"showId\":\"SHOW-01\",\"seatId\":\"Z9\"}");
        assertEquals("SEAT_NOT_FOUND", session.last().path("errorCode").asText());
        send(dispatcher, session, "{\"type\":\"CANCEL_BOOKING\",\"requestId\":\"4\",\"bookingId\":\"../bad\"}");
        assertEquals("INVALID_REQUEST_FORMAT", session.last().path("errorCode").asText());
        send(dispatcher, session, "{\"type\":\"GET_SEATS\",\"requestId\":\"5\",\"showId\":\"SHOW-01\"}");
        assertEquals("SEAT_SNAPSHOT", session.last().path("type").asText());
        send(dispatcher, session, "{\"type\":\"LOGOUT\",\"requestId\":\"6\"}");
        assertEquals("LOGOUT_OK", session.last().path("type").asText());
        send(dispatcher, session, "{\"type\":\"GET_SEATS\",\"requestId\":\"7\",\"showId\":\"SHOW-01\"}");
        assertEquals("AUTH_REQUIRED", session.last().path("errorCode").asText());
    }

    private static boolean allCleared(char[] value) {
        for (char c : value) {
            if (c != '\0') return false;
        }
        return true;
    }

    private static String login(String username, String password) {
        return ProtocolJson.serialize(java.util.Map.of("type", "LOGIN", "requestId", "login",
            "username", username, "password", password));
    }

    private static void send(SecurityRequestDispatcher dispatcher, Session session, String line) {
        dispatcher.dispatch(session, line);
    }

    private static final class Session implements RequestContext {
        private String user;
        private final List<String> replies = new ArrayList<>();
        public String getSessionId() { return "test"; }
        public Optional<String> getAuthenticatedUserId() { return Optional.ofNullable(user); }
        public void setAuthenticatedUserId(String value) { user = value; }
        public void clearAuthentication() { user = null; }
        public void send(String line) { replies.add(line); }
        public boolean isOpen() { return true; }
        public void close() {}
        JsonNode last() { return ProtocolJson.parseTree(replies.get(replies.size() - 1)); }
    }
}
