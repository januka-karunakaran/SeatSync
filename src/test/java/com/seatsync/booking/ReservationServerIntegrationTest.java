package com.seatsync.booking;

import com.seatsync.client.ClientMessageHandler;
import com.seatsync.client.TcpClient;
import com.seatsync.common.model.AuthResult;
import com.seatsync.common.model.ErrorCode;
import com.seatsync.common.model.SeatStatus;
import com.seatsync.common.protocol.ProtocolJson;
import com.seatsync.common.protocol.event.SeatUpdatedEvent;
import com.seatsync.common.protocol.reply.BookOkReply;
import com.seatsync.common.protocol.reply.BookRejectedReply;
import com.seatsync.common.protocol.reply.CancelOkReply;
import com.seatsync.common.protocol.reply.CancelRejectedReply;
import com.seatsync.common.protocol.reply.LoginOkReply;
import com.seatsync.common.protocol.request.BookSeatRequest;
import com.seatsync.common.protocol.request.CancelBookingRequest;
import com.seatsync.common.protocol.request.LoginRequest;
import com.seatsync.concurrency.BookingRequestProcessor;
import com.seatsync.concurrency.WorkerRequestDispatcher;
import com.seatsync.security.AuthorizationService;
import com.seatsync.security.SecurityRequestDispatcher;
import com.seatsync.common.contracts.AuthService;
import com.seatsync.common.protocol.request.GetShowsRequest;
import com.seatsync.common.protocol.request.GetShowDetailsRequest;
import com.seatsync.common.protocol.request.GetSeatsRequest;
import com.seatsync.common.protocol.reply.ShowListReply;
import com.seatsync.common.protocol.reply.ShowDetailsReply;
import com.seatsync.common.protocol.reply.ErrorReply;
import com.seatsync.common.protocol.reply.SeatSnapshotReply;
import com.seatsync.network.NioBookingServer;
import com.seatsync.network.ServerConfig;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real reservation service, worker dispatcher and NIO transport; only auth is a test stub. */
class ReservationServerIntegrationTest {
    private static BlockingQueue<SeatUpdatedEvent> subscribe(TcpClient client) {
        BlockingQueue<SeatUpdatedEvent> events = new LinkedBlockingQueue<>();
        client.getMessageHandler().addListener(new ClientMessageHandler.Listener() {
            @Override public void onSeatUpdated(SeatUpdatedEvent event) { events.add(event); }
        });
        return events;
    }

    private static void assertUpdate(BlockingQueue<SeatUpdatedEvent> events, long version, SeatStatus status)
        throws InterruptedException {
        var event = events.poll(5, TimeUnit.SECONDS);
        assertNotNull(event, "Missing real TCP seat update");
        assertEquals(version, event.version());
        assertEquals(40, event.seats().size());
        assertEquals(status, event.seats().get(4).status());
    }

    @Test
    void catalogAndSeatInventoriesAreIsolatedThroughTheAuthenticatedServer() throws Exception {
        var service = new InMemoryReservationService();
        AuthService auth = (user, password) -> AuthResult.ok("id-" + user, user, "ok");
        var gate = new SecurityRequestDispatcher(auth, new AuthorizationService(), new BookingRequestProcessor(auth, service));
        try (var dispatcher = new WorkerRequestDispatcher(gate)) {
            var server = new NioBookingServer(ServerConfig.of("127.0.0.1", 0), dispatcher);
            try {
                server.start();
                try (var client = new TcpClient("127.0.0.1", server.getPort())) {
                    client.connect(2000);
                    assertInstanceOf(LoginOkReply.class, client.sendRequest(new LoginRequest("l", "alice", "test"))
                        .get(5, TimeUnit.SECONDS));
                    var details = assertInstanceOf(ShowDetailsReply.class, client.sendRequest(
                        new GetShowDetailsRequest("d", "SHOW-02")).get(5, TimeUnit.SECONDS));
                    assertEquals(120, details.showDetails().layout().totalCapacity());
                    var unknown = assertInstanceOf(ErrorReply.class, client.sendRequest(
                        new GetShowDetailsRequest("x", "missing")).get(5, TimeUnit.SECONDS));
                    assertEquals(ErrorCode.SHOW_NOT_FOUND, unknown.errorCode());
                    var first = assertInstanceOf(BookOkReply.class, client.sendRequest(
                        new BookSeatRequest("a", "SHOW-01", "A1")).get(5, TimeUnit.SECONDS));
                    assertInstanceOf(BookOkReply.class, client.sendRequest(
                        new BookSeatRequest("b", "SHOW-02", "A1")).get(5, TimeUnit.SECONDS));
                    assertInstanceOf(BookOkReply.class, client.sendRequest(
                        new BookSeatRequest("c", "SHOW-02", "J12")).get(5, TimeUnit.SECONDS));
                    assertInstanceOf(CancelOkReply.class, client.sendRequest(
                        new CancelBookingRequest("cancel", first.bookingId())).get(5, TimeUnit.SECONDS));
                    var list = assertInstanceOf(ShowListReply.class, client.sendRequest(
                        new GetShowsRequest("list")).get(5, TimeUnit.SECONDS));
                    assertEquals(40, list.shows().get(0).availableSeats());
                    assertEquals(118, list.shows().get(1).availableSeats());
                    var snapshot = assertInstanceOf(SeatSnapshotReply.class, client.sendRequest(
                        new GetSeatsRequest("map", "SHOW-02")).get(5, TimeUnit.SECONDS));
                    assertEquals(120, snapshot.seats().size());
                    assertEquals(SeatStatus.BOOKED, snapshot.seats().get(0).status());
                    assertEquals(2, snapshot.version());
                }
            } finally {
                server.stop();
            }
        }
    }

    @Test
    void realServiceBooksRejectsCrossUserCancellationAndBroadcastsToTwoClients() throws Exception {
        var service = new InMemoryReservationService();
        try (var dispatcher = new WorkerRequestDispatcher(
            (username, password) -> AuthResult.ok("id-" + username, username, "test login"), service)) {
            var server = new NioBookingServer(ServerConfig.of("127.0.0.1", 0), dispatcher);
            service.addSeatChangeListener((show, version, seats) -> {
                String json = ProtocolJson.serialize(new SeatUpdatedEvent(show, version, seats));
                server.getSessionRegistry().getAll().stream()
                    .filter(session -> session.isOpen() && session.isAuthenticated())
                    .forEach(session -> session.send(json));
            });
            try {
                server.start();
                try (var alice = new TcpClient("127.0.0.1", server.getPort());
                     var bob = new TcpClient("127.0.0.1", server.getPort())) {
                    alice.connect(2000);
                    bob.connect(2000);
                    var first = subscribe(alice);
                    var second = subscribe(bob);
                    assertInstanceOf(LoginOkReply.class,
                        alice.sendRequest(new LoginRequest("login-a", "alice", "test")).get(5, TimeUnit.SECONDS));
                    assertInstanceOf(LoginOkReply.class,
                        bob.sendRequest(new LoginRequest("login-b", "bob", "test")).get(5, TimeUnit.SECONDS));
                    var booking = assertInstanceOf(BookOkReply.class,
                        alice.sendRequest(new BookSeatRequest("book-a", "SHOW-01", "A5")).get(5, TimeUnit.SECONDS));
                    assertUpdate(first, 1, SeatStatus.BOOKED);
                    assertUpdate(second, 1, SeatStatus.BOOKED);
                    var rejected = assertInstanceOf(BookRejectedReply.class,
                        bob.sendRequest(new BookSeatRequest("book-b", "SHOW-01", "A5")).get(5, TimeUnit.SECONDS));
                    assertEquals(ErrorCode.SEAT_ALREADY_BOOKED, rejected.errorCode());
                    var denied = assertInstanceOf(CancelRejectedReply.class,
                        bob.sendRequest(new CancelBookingRequest("cancel-b", booking.bookingId())).get(5, TimeUnit.SECONDS));
                    assertEquals(ErrorCode.NOT_BOOKING_OWNER, denied.errorCode());
                    assertInstanceOf(CancelOkReply.class,
                        alice.sendRequest(new CancelBookingRequest("cancel-a", booking.bookingId())).get(5, TimeUnit.SECONDS));
                    assertUpdate(first, 2, SeatStatus.AVAILABLE);
                    assertUpdate(second, 2, SeatStatus.AVAILABLE);
                    assertTrue(first.isEmpty());
                    assertTrue(second.isEmpty());
                    assertInstanceOf(BookOkReply.class,
                        bob.sendRequest(new BookSeatRequest("rebook", "SHOW-01", "A5")).get(5, TimeUnit.SECONDS));
                    assertUpdate(first, 3, SeatStatus.BOOKED);
                    assertUpdate(second, 3, SeatStatus.BOOKED);
                }
            } finally {
                server.stop();
            }
        }
    }
}
