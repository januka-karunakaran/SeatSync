package com.seatsync.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.seatsync.booking.InMemoryReservationService;
import com.seatsync.common.contracts.RequestContext;
import com.seatsync.common.model.*;
import com.seatsync.common.protocol.ProtocolJson;
import com.seatsync.concurrency.BookingRequestProcessor;
import com.seatsync.network.metrics.NetworkMetricsTracker;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdminSecurityTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-22T00:00:00Z"), ZoneOffset.UTC);
    private static final String CUSTOMER_PASSWORD = "test-only-customer-password";
    private static final String ADMIN_PASSWORD = "test-only-separate-admin-password";
    private static DemoUserRepository users;
    private static AuthService auth;

    @BeforeAll static void accounts() {
        var hasher = new PasswordHasher();
        users = new DemoUserRepository(CUSTOMER_PASSWORD.toCharArray(), ADMIN_PASSWORD.toCharArray(), hasher);
        auth = new AuthService(users, hasher);
    }

    static final class Session implements RequestContext {
        private String user;
        private final List<String> replies = new ArrayList<>();
        Session(String user) { this.user = user; }
        public String getSessionId() { return "test-session"; }
        public Optional<String> getAuthenticatedUserId() { return Optional.ofNullable(user); }
        public void setAuthenticatedUserId(String value) { user = value; }
        public void clearAuthentication() { user = null; }
        public void send(String line) { replies.add(line); }
        public boolean isOpen() { return true; }
        public void close() { clearAuthentication(); }
        JsonNode last() { return ProtocolJson.parseTree(replies.get(replies.size() - 1)); }
    }

    static final class Harness {
        final InMemoryReservationService catalog = new InMemoryReservationService();
        final AuthorizationService authorization = new AuthorizationService(users);
        final SecurityAuditLog audit = new SecurityAuditLog(CLOCK, 1000);
        final List<ShowDetails> changed = new ArrayList<>();
        final AdminService admin = new AdminService(authorization, catalog, catalog::registerShow, new HoldAdminBackend(authorization, catalog),
            audit, CLOCK, changed::add);
        final NetworkMetricsTracker metrics = new NetworkMetricsTracker();
        final AdminRequestDispatcher privileged = new AdminRequestDispatcher(authorization, admin, audit,
            metrics::getNetworkMetricsSnapshot, metrics::getMetricsSnapshot, new BookingRequestProcessor(auth, catalog));
        final SecurityRequestDispatcher dispatcher = new SecurityRequestDispatcher(auth, authorization, privileged, audit);
        JsonNode send(Session session, Map<String, ?> message) {
            dispatcher.dispatch(session, ProtocolJson.serialize(message));
            return session.last();
        }
    }

    static ShowDetails show(String id) {
        return new ShowDetails(id, "New movie", "Description", "Drama", "2030-01-01T18:00:00Z",
            "2030-01-01T20:00:00Z", "Main room", new AuditoriumLayout("LAYOUT-NEW", "Room", 5, 8, 40, List.of()));
    }

    @Test void rolesComeFromServerAccountsAndLegacyDefaultsAreCustomers() {
        var authorization = new AuthorizationService(users);
        assertEquals(UserRole.CUSTOMER, users.findByUsername("alice").orElseThrow().role());
        assertEquals(UserRole.ADMIN, users.findByUsername("admin").orElseThrow().role());
        assertFalse(authorization.isAdmin("ADM-forged"));
        assertFalse(authorization.isAdmin("user-admin"));
        assertFalse(new AuthorizationService().isAdmin("demo-admin"));
        assertEquals(Optional.of(UserRole.ADMIN), authorization.roleOf(new Session("demo-admin")));
        var legacy = new User("id", "admin", new byte[16], new byte[32]);
        assertEquals(UserRole.CUSTOMER, legacy.role());
        assertTrue(authorization.canReadOwnedResource(new Session("demo-alice"), "demo-alice"));
        assertFalse(authorization.canReadOwnedResource(new Session("demo-bob"), "demo-alice"));
        assertTrue(authorization.canReadOwnedResource(new Session("demo-admin"), "demo-alice"));
        assertFalse(authorization.canReadOwnedResource(new Session(null), "demo-alice"));
    }

    @Test void loginBindsAdminIdentityOnlyAfterCorrectPasswordAndLogoutRevokesAccess() {
        var h = new Harness();
        var session = new Session(null);
        assertEquals("LOGIN_FAILED", h.send(session, Map.of("type", "LOGIN", "requestId", "bad-login",
            "username", "admin", "password", CUSTOMER_PASSWORD)).path("type").asText());
        assertFalse(session.isAuthenticated());
        assertEquals("LOGIN_OK", h.send(session, Map.of("type", "LOGIN", "requestId", "good-login",
            "username", "admin", "password", ADMIN_PASSWORD)).path("type").asText());
        assertEquals("NETWORK_METRICS_REPLY", h.send(session, Map.of("type", "GET_NETWORK_METRICS", "requestId", "metrics"))
            .path("type").asText());
        h.send(session, Map.of("type", "LOGOUT", "requestId", "logout"));
        assertEquals("AUTH_REQUIRED", h.send(session, Map.of("type", "GET_NETWORK_METRICS", "requestId", "after"))
            .path("errorCode").asText());
    }

    @Test void everyAdminRouteAndMetricsGateDenyCustomersAndAnonymous() {
        var h = new Harness();
        List<Map<String, ?>> requests = List.of(
            Map.of("type", "ADMIN_ADD_SHOW", "requestId", "add", "showDetails", show("SHOW-03")),
            Map.of("type", "ADMIN_UPDATE_SEAT_STATUS", "requestId", "update", "showId", "SHOW-01", "seatId", "A1", "status", "AVAILABLE"),
            Map.of("type", "ADMIN_CANCEL_HOLD", "requestId", "hold", "holdId", "HLD-test", "reason", "test"),
            Map.of("type", "ADMIN_GET_AUDIT_LOGS", "requestId", "audit", "limit", 10),
            Map.of("type", "GET_NETWORK_METRICS", "requestId", "network"),
            Map.of("type", "GET_SERVER_METRICS", "requestId", "server"));
        for (var request : requests) {
            assertEquals("FORBIDDEN_ROLE", h.send(new Session("demo-alice"), request).path("errorCode").asText());
            assertEquals("AUTH_REQUIRED", h.send(new Session(null), request).path("errorCode").asText());
        }
        assertTrue(h.catalog.getShowDetails("SHOW-03").isEmpty());
        assertThrows(SecurityException.class, () -> h.admin.addShow("demo-alice", show("SHOW-03")));
    }

    @Test void validAdminCreationUsesRealCatalogAndEmitsOneCallback() {
        var h = new Harness();
        var request = Map.of("type", "ADMIN_ADD_SHOW", "requestId", "create", "showDetails", show("SHOW-03"));
        assertEquals("ADMIN_SHOW_ADDED", h.send(new Session("demo-admin"), request).path("type").asText());
        assertTrue(h.catalog.getShowDetails("SHOW-03").isPresent());
        assertEquals(40, h.catalog.snapshot("SHOW-03").seats().size());
        assertEquals(1, h.changed.size());
        assertEquals("SHOW_ALREADY_EXISTS", h.send(new Session("demo-admin"), request).path("errorCode").asText());
        assertEquals(1, h.changed.size());
    }

    @Test void forgedActorRoleAndMalformedAdminPayloadNeverReachMutation() {
        var h = new Harness();
        for (String field : List.of("userId", "role", "actor", "isAdmin")) {
            var frame = new java.util.HashMap<String, Object>(Map.of("type", "ADMIN_ADD_SHOW", "requestId", "forged",
                "showDetails", show("SHOW-03")));
            frame.put(field, "demo-admin");
            assertEquals("INVALID_REQUEST_FORMAT", h.send(new Session("demo-alice"), frame).path("errorCode").asText());
        }
        assertEquals("INVALID_REQUEST_FORMAT", h.send(new Session("demo-admin"), Map.of("type", "ADMIN_ADD_SHOW",
            "requestId", "bad", "showDetails", Map.of("title", "missing fields"))).path("errorCode").asText());
        assertEquals("INVALID_REQUEST_FORMAT", h.send(new Session("demo-admin"), Map.of("type", "ADMIN_GET_AUDIT_LOGS",
            "requestId", "bad-limit", "limit", 100000)).path("errorCode").asText());
        assertTrue(h.catalog.getShowDetails("SHOW-03").isEmpty());
    }

    @Test void showPolicyRejectsBadTimesCapacityAndNegativeStateTransitions() {
        var h = new Harness();
        var good = show("SHOW-03");
        var badTime = new ShowDetails(good.showId(), good.title(), good.description(), good.genre(), "yesterday",
            good.endTime(), good.venue(), good.layout());
        var badCapacity = new ShowDetails(good.showId(), good.title(), good.description(), good.genre(), good.startTime(),
            good.endTime(), good.venue(), new AuditoriumLayout("L", "Room", 5, 8, 500, List.of()));
        for (var bad : List.of(badTime, badCapacity)) {
            assertEquals("INVALID_REQUEST_FORMAT", h.send(new Session("demo-admin"), Map.of("type", "ADMIN_ADD_SHOW",
                "requestId", "bad-policy", "showDetails", bad)).path("errorCode").asText());
        }
        assertTrue(h.changed.isEmpty());
        assertEquals("HOLD_NOT_FOUND", h.send(new Session("demo-admin"), Map.of("type", "ADMIN_CANCEL_HOLD",
            "requestId", "no-backend", "holdId", "HLD-test", "reason", "test")).path("errorCode").asText());
    }

    @Test void adminCanReleaseAnotherUsersHoldThroughAtomicBookingBackend() {
        var h = new Harness();
        var backend = new HoldAdminBackend(h.authorization, h.catalog);
        var admin = new AdminService(h.authorization, h.catalog, h.catalog::registerShow, backend, h.audit, CLOCK, h.changed::add);
        var hold = h.catalog.holdSeat("demo-alice", "SHOW-01", "A1", 120000);
        assertTrue(hold.success());
        assertThrows(SecurityException.class, () -> admin.cancelHold("demo-bob", hold.holdId(), "override"));
        assertTrue(h.catalog.getHold(hold.holdId()).isPresent());
        assertTrue(admin.cancelHold("demo-admin", hold.holdId(), "administrative release"));
        assertTrue(h.catalog.getHold(hold.holdId()).isEmpty());
        assertEquals(SeatStatus.AVAILABLE, h.catalog.snapshot("SHOW-01").seats().get(0).status());
        assertFalse(admin.cancelHold("demo-admin", hold.holdId(), "repeat"));
        assertTrue(admin.updateSeatStatus("demo-admin", "SHOW-01", "A1", SeatStatus.BOOKED));
        assertTrue(admin.updateSeatStatus("demo-admin", "SHOW-01", "A1", SeatStatus.AVAILABLE));
        assertTrue(h.catalog.book("demo-bob", "SHOW-01", "A1").success());
    }

    @Test void auditIsBoundedImmutableAndDoesNotRetainRequestSecrets() {
        var h = new Harness();
        var session = new Session(null);
        h.send(session, Map.of("type", "LOGIN", "requestId", "login", "username", "admin", "password", ADMIN_PASSWORD));
        h.send(new Session("demo-alice"), Map.of("type", "ADMIN_CANCEL_HOLD", "requestId", "denied",
            "holdId", "HLD-test", "reason", "token-secret-never-log"));
        String logs = ProtocolJson.serialize(h.audit.recent(20));
        assertFalse(logs.contains(ADMIN_PASSWORD));
        assertFalse(logs.contains("token-secret-never-log"));
        assertTrue(logs.contains("requestId=denied"));
        assertTrue(logs.contains("outcome=DENIED"));
        assertThrows(UnsupportedOperationException.class, () -> h.audit.recent(20).clear());
        var bounded = new SecurityAuditLog(CLOCK, 2);
        for (int i = 0; i < 3; i++) { bounded.record("alice", "LOGIN", "r-" + i, SecurityAuditLog.Outcome.SUCCESS); }
        assertEquals(2, bounded.recent(20).size());
        assertFalse(bounded.recent(20).get(0).detail().contains("r-0"));
    }
}
