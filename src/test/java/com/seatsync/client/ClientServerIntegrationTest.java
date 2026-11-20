package com.seatsync.client;

import com.seatsync.common.model.ProtocolConstants;
import com.seatsync.common.model.SeatStatus;
import com.seatsync.common.model.SeatView;
import com.seatsync.common.protocol.ReplyMessage;
import com.seatsync.common.protocol.event.SeatUpdatedEvent;
import com.seatsync.common.protocol.reply.BookOkReply;
import com.seatsync.common.protocol.reply.CancelOkReply;
import com.seatsync.common.protocol.reply.LoginOkReply;
import com.seatsync.common.protocol.reply.SeatSnapshotReply;
import com.seatsync.common.protocol.request.BookSeatRequest;
import com.seatsync.common.protocol.request.CancelBookingRequest;
import com.seatsync.common.protocol.request.GetSeatsRequest;
import com.seatsync.common.protocol.request.LoginRequest;
import com.seatsync.network.NioBookingServer;
import com.seatsync.network.ServerConfig;
import com.seatsync.server.MockRequestDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end integration test validating {@link TcpClient} communication
 * directly with {@link NioBookingServer}.
 * <p>
 * Demonstrates:
 * <ul>
 *   <li>Point-to-point TCP stream establishment.</li>
 *   <li>Full request-response cycle for Login, GetSeats, BookSeat, and CancelBooking.</li>
 *   <li>Server-to-client asynchronous broadcast events over TCP.</li>
 * </ul>
 * <p>
 * Owned by Member 01 (TCP Socket Programming & Desktop Client).
 */
class ClientServerIntegrationTest {

    private NioBookingServer server;
    private int serverPort;
    private TcpClient client;

    @BeforeEach
    void setUp() throws IOException {
        ServerConfig config = ServerConfig.of("127.0.0.1", 0);
        server = new NioBookingServer(config, new MockRequestDispatcher());
        server.start();
        serverPort = server.getPort();
        assertTrue(serverPort > 0, "Server must bind to dynamic port");
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.disconnect();
        }
        if (server != null) {
            server.stop();
        }
    }

    @Test
    @DisplayName("End-to-end TCP client exchange with NIO Server: Login, GetSeats, BookSeat, Cancel, and Broadcast")
    void testFullClientServerLifecycle() throws Exception {
        client = new TcpClient("127.0.0.1", serverPort);
        client.connect(2000);
        assertTrue(client.isConnected());

        // 1. Submit LOGIN
        LoginRequest loginReq = new LoginRequest(client.nextRequestId(), "alice", "password123");
        CompletableFuture<ReplyMessage> loginFuture = client.sendRequest(loginReq);
        ReplyMessage loginReply = loginFuture.get(3, TimeUnit.SECONDS);
        assertInstanceOf(LoginOkReply.class, loginReply);
        LoginOkReply loginOk = (LoginOkReply) loginReply;
        assertEquals("alice", loginOk.username());
        assertNotNull(loginOk.userId());

        // 2. Submit GET_SEATS
        GetSeatsRequest getSeatsReq = new GetSeatsRequest(client.nextRequestId(), ProtocolConstants.DEFAULT_SHOW_ID);
        CompletableFuture<ReplyMessage> seatsFuture = client.sendRequest(getSeatsReq);
        ReplyMessage seatsReply = seatsFuture.get(3, TimeUnit.SECONDS);
        assertInstanceOf(SeatSnapshotReply.class, seatsReply);
        SeatSnapshotReply snapshot = (SeatSnapshotReply) seatsReply;
        assertEquals(40, snapshot.seats().size());

        // 3. Submit BOOK_SEAT
        BookSeatRequest bookReq = new BookSeatRequest(client.nextRequestId(), ProtocolConstants.DEFAULT_SHOW_ID, "C4");
        CompletableFuture<ReplyMessage> bookFuture = client.sendRequest(bookReq);
        ReplyMessage bookReply = bookFuture.get(3, TimeUnit.SECONDS);
        assertInstanceOf(BookOkReply.class, bookReply);
        BookOkReply bookOk = (BookOkReply) bookReply;
        assertEquals("C4", bookOk.seatId());
        assertNotNull(bookOk.bookingId());

        // 4. Submit CANCEL_BOOKING
        CancelBookingRequest cancelReq = new CancelBookingRequest(client.nextRequestId(), bookOk.bookingId());
        CompletableFuture<ReplyMessage> cancelFuture = client.sendRequest(cancelReq);
        ReplyMessage cancelReply = cancelFuture.get(3, TimeUnit.SECONDS);
        assertInstanceOf(CancelOkReply.class, cancelReply);
        CancelOkReply cancelOk = (CancelOkReply) cancelReply;
        assertEquals(bookOk.bookingId(), cancelOk.bookingId());

        // 5. Test Server Asynchronous Broadcast
        CountDownLatch broadcastLatch = new CountDownLatch(1);
        AtomicReference<SeatUpdatedEvent> receivedEvent = new AtomicReference<>();

        client.getMessageHandler().addListener(new ClientMessageHandler.Listener() {
            @Override
            public void onSeatUpdated(SeatUpdatedEvent event) {
                receivedEvent.set(event);
                broadcastLatch.countDown();
            }
        });

        SeatUpdatedEvent testBroadcast = new SeatUpdatedEvent(
            ProtocolConstants.DEFAULT_SHOW_ID,
            2L,
            List.of(new SeatView("D5", SeatStatus.BOOKED, "USR-999"))
        );
        server.getSessionRegistry().broadcast(com.seatsync.common.protocol.ProtocolJson.serialize(testBroadcast));

        assertTrue(broadcastLatch.await(3, TimeUnit.SECONDS), "Client must receive server broadcast event");
        assertNotNull(receivedEvent.get());
        assertEquals(1, receivedEvent.get().seats().size());
        assertEquals("D5", receivedEvent.get().seats().get(0).seatId());
    }
}
