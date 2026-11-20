package com.seatsync.concurrency;

import com.fasterxml.jackson.databind.JsonNode;
import com.seatsync.common.contracts.*;
import com.seatsync.common.model.*;
import com.seatsync.common.protocol.ProtocolJson;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkerRequestDispatcherTest {
    private static final String LOGIN = """
        {"type":"LOGIN","requestId":"login","username":"alice","password":"sample"}
        """;
    private static final String BOOK = """
        {"type":"BOOK_SEAT","requestId":"book","showId":"SHOW-01","seatId":"A1"}
        """;

    private static final class Session implements RequestContext {
        private final String id = UUID.randomUUID().toString();
        private final BlockingQueue<String> replies = new LinkedBlockingQueue<>();
        private volatile String user;
        public String getSessionId() { return id; }
        public Optional<String> getAuthenticatedUserId() { return Optional.ofNullable(user); }
        public void setAuthenticatedUserId(String value) { user = value; }
        public void clearAuthentication() { user = null; }
        public boolean isOpen() { return true; }
        public void close() {}
        public void send(String line) { replies.add(line); }

        JsonNode next() throws InterruptedException {
            String reply = replies.poll(5, TimeUnit.SECONDS);
            assertNotNull(reply, "Timed out waiting for a worker reply");
            return ProtocolJson.parseTree(reply);
        }
    }

    @Test
    void twentySessionsRunOnFourNamedWorkers() throws Exception {
        var workers = ConcurrentHashMap.<String>newKeySet();
        var firstFour = new CountDownLatch(4);
        AuthService auth = (username, password) -> {
            workers.add(Thread.currentThread().getName());
            firstFour.countDown();
            try {
                assertTrue(firstFour.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
            return AuthResult.ok("server-id", username, "ok");
        };
        try (var dispatcher = new WorkerRequestDispatcher(auth, null)) {
            List<Session> sessions = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                Session session = new Session();
                sessions.add(session);
                dispatcher.dispatch(session, LOGIN);
            }
            for (Session session : sessions) {
                assertEquals("LOGIN_OK", session.next().path("type").asText());
            }
            assertEquals(4, workers.size());
            assertTrue(workers.stream().allMatch(name -> name.startsWith("seatsync-worker-")));
        }
    }

    @Test
    void pipelinedRequestsRespectSessionIdentityAndArrivalOrder() throws Exception {
        var reservations = new ReservationService() {
            public BookingResult book(String user, String show, String seat) {
                assertEquals("server-id", user);
                return BookingResult.ok("booking", show, seat, "ok");
            }
            public CancelResult cancel(String user, String booking) {
                assertEquals("server-id", user);
                return CancelResult.ok(booking, "A1", "ok");
            }
            public SeatSnapshot snapshot(String show) { return new SeatSnapshot(show, 1, List.of()); }
        };
        try (var dispatcher = new WorkerRequestDispatcher((u, p) -> AuthResult.ok("server-id", u, "ok"), reservations)) {
            var session = new Session();
            dispatcher.dispatch(session, BOOK);
            dispatcher.dispatch(session, LOGIN);
            dispatcher.dispatch(session, BOOK.replace("\"seatId\"", "\"userId\":\"other\",\"seatId\""));
            dispatcher.dispatch(session, BOOK);
            dispatcher.dispatch(session, """
                {"type":"GET_SEATS","requestId":"s","showId":"SHOW-01"}
                """);
            dispatcher.dispatch(session, """
                {"type":"CANCEL_BOOKING","requestId":"c","bookingId":"booking"}
                """);
            dispatcher.dispatch(session, """
                {"type":"LOGOUT","requestId":"l"}
                """);
            dispatcher.dispatch(session, BOOK);
            assertEquals("AUTH_REQUIRED", session.next().path("errorCode").asText());
            assertEquals("LOGIN_OK", session.next().path("type").asText());
            assertEquals("INVALID_REQUEST_FORMAT", session.next().path("errorCode").asText());
            for (String type : List.of("BOOK_OK", "SEAT_SNAPSHOT", "CANCEL_OK", "LOGOUT_OK")) {
                assertEquals(type, session.next().path("type").asText());
            }
            var denied = session.next();
            assertEquals("AUTH_REQUIRED", denied.path("errorCode").asText());
            assertEquals("book", denied.path("requestId").asText());
        }
    }

    @Test
    void failuresReturnSafeRepliesAndWorkerContinues() throws Exception {
        try (var dispatcher = new WorkerRequestDispatcher((u, p) -> {
            throw new IllegalStateException("private service detail");
        }, null)) {
            var session = new Session();
            dispatcher.dispatch(session, LOGIN);
            dispatcher.dispatch(session, "{broken");
            dispatcher.dispatch(session, BOOK);
            var failure = session.next();
            assertEquals("INTERNAL_ERROR", failure.path("errorCode").asText());
            assertFalse(failure.toString().contains("private service detail"));
            assertEquals("INVALID_REQUEST_FORMAT", session.next().path("errorCode").asText());
            assertEquals("AUTH_REQUIRED", session.next().path("errorCode").asText());
        }
    }
}
