package com.seatsync.client;

import com.seatsync.booking.InMemoryReservationService;
import com.seatsync.common.contracts.AuthService;
import com.seatsync.common.model.AuthResult;
import com.seatsync.common.protocol.reply.SeatSnapshotReply;
import com.seatsync.common.protocol.request.GetSeatsRequest;
import com.seatsync.concurrency.BookingRequestProcessor;
import com.seatsync.concurrency.WorkerRequestDispatcher;
import com.seatsync.delivery.ShowSubscriptionRegistry;
import com.seatsync.network.NioBookingServer;
import com.seatsync.network.ServerConfig;
import com.seatsync.reliability.ReconnectCoordinator;
import com.seatsync.security.AuthorizationService;
import com.seatsync.security.SecurityRequestDispatcher;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionRecoveryTest {

    @Test
    void droppedClientSignsInAgainAndReadsTheSeatSnapshot() throws Exception {
        var reservations = new InMemoryReservationService();
        var registry = new ShowSubscriptionRegistry();
        AuthService auth = (username, password) -> AuthResult.ok("demo-" + username, username, "ok");
        var booking = new BookingRequestProcessor(auth, reservations, registry);
        var security = new SecurityRequestDispatcher(auth, new AuthorizationService(), booking);
        try (var dispatcher = new WorkerRequestDispatcher(security)) {
            var server = new NioBookingServer(ServerConfig.of("127.0.0.1", 0), dispatcher);
            server.start();
            try (var client = new TcpClient("127.0.0.1", server.getPort())) {
                client.connect(3000);
                client.disconnect();
                var coordinator = new ReconnectCoordinator(1, 2.0, 20, 3);
                coordinator.trackShowSubscription("SHOW-01");
                assertTrue(new SessionRecovery().recover(client, coordinator, "alice", "password123"));
                var snapshot = client.sendRequest(new GetSeatsRequest(client.nextRequestId(), "SHOW-01"))
                    .get(5, TimeUnit.SECONDS);
                assertInstanceOf(SeatSnapshotReply.class, snapshot);
            } finally {
                server.stop();
            }
        }
    }
}
