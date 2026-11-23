package com.seatsync.client;

import com.seatsync.booking.InMemoryReservationService;
import com.seatsync.common.contracts.AuthService;
import com.seatsync.common.model.AuthResult;
import com.seatsync.concurrency.BookingRequestProcessor;
import com.seatsync.concurrency.WorkerRequestDispatcher;
import com.seatsync.delivery.ShowEventBus;
import com.seatsync.delivery.ShowSubscriptionRegistry;
import com.seatsync.network.NioBookingServer;
import com.seatsync.network.ServerConfig;
import com.seatsync.reliability.IdempotencyCache;
import com.seatsync.reliability.IdempotentRequestDispatcher;
import com.seatsync.security.AuthorizationService;
import com.seatsync.security.SecurityRequestDispatcher;
import com.seatsync.tickets.TicketManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("VirtualClientLoadHarness Concurrency & Contention Tests (U16)")
class VirtualClientLoadHarnessTest {

    private NioBookingServer server;
    private WorkerRequestDispatcher dispatcher;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        var reservations = new InMemoryReservationService();
        var subscriptionRegistry = new ShowSubscriptionRegistry();
        var eventBus = new ShowEventBus(subscriptionRegistry);
        var ticketManager = new TicketManager();

        AuthService auth = (username, password) -> AuthResult.ok("USR-" + username.toUpperCase(), username, "ok");
        AuthorizationService authorization = new AuthorizationService();

        var bookingProcessor = new BookingRequestProcessor(auth, reservations, subscriptionRegistry, ticketManager, ticketManager);
        var securityDispatcher = new SecurityRequestDispatcher(auth, authorization, bookingProcessor);
        var idempotentDispatcher = new IdempotentRequestDispatcher(securityDispatcher, new IdempotencyCache());

        dispatcher = new WorkerRequestDispatcher(idempotentDispatcher);
        server = new NioBookingServer(ServerConfig.of("127.0.0.1", 0), dispatcher);
        server.start();
        port = server.getPort();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) {
            server.stop();
        }
        if (dispatcher != null) {
            dispatcher.close();
        }
    }

    @Test
    @DisplayName("20 Virtual Clients: Contending for 5 seats yields exactly 5 bookings and zero double-bookings")
    void testContentionBenchmark20Clients() throws Exception {
        VirtualClientLoadHarness harness = new VirtualClientLoadHarness("127.0.0.1", port, "password123");
        List<String> targetSeats = List.of("A1", "A2", "A3", "A4", "A5");

        VirtualClientLoadHarness.BenchmarkResult result = harness.runContentionBenchmark(20, "SHOW-01", targetSeats);

        assertEquals(20, result.totalClients());
        assertEquals(5, result.successCount(), "Exactly 5 unique seats must be booked");
        assertEquals(15, result.conflictCount(), "Remaining 15 clients must receive conflict/rejection");
        assertEquals(0, result.errorCount(), "Must have zero network or framing errors");
        assertTrue(result.hasZeroDoubleBookings());
        assertEquals(5, result.seatWinners().size());

        assertTrue(result.meanLatencyMs() >= 0.0);
        assertTrue(result.p95LatencyMs() >= 0.0);
    }

    @Test
    @DisplayName("50 Virtual Clients: Contending for 10 seats guarantees atomic correctness under heavy load")
    void testContentionBenchmark50Clients() throws Exception {
        VirtualClientLoadHarness harness = new VirtualClientLoadHarness("127.0.0.1", port, "password123");
        List<String> targetSeats = List.of("B1", "B2", "B3", "B4", "B5", "B6", "B7", "B8", "C1", "C2");

        VirtualClientLoadHarness.BenchmarkResult result = harness.runContentionBenchmark(50, "SHOW-01", targetSeats);

        assertEquals(50, result.totalClients());
        assertEquals(10, result.successCount(), "Exactly 10 unique seats must be successfully reserved");
        assertEquals(40, result.conflictCount(), "Remaining 40 contending clients must be cleanly rejected");
        assertEquals(0, result.errorCount());
        assertTrue(result.hasZeroDoubleBookings(), "Correctness invariant: zero double bookings allowed");
        assertEquals(10, result.seatWinners().size());

        assertTrue(result.p95LatencyMs() < 2000.0, "p95 latency should remain well under 2 seconds on localhost");
    }
}
