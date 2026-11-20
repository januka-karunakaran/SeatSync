package com.seatsync.network;

import com.seatsync.common.model.ErrorCode;
import com.seatsync.common.model.ProtocolConstants;
import com.seatsync.common.protocol.Message;
import com.seatsync.common.protocol.ProtocolJson;
import com.seatsync.common.protocol.reply.BookOkReply;
import com.seatsync.common.protocol.reply.ErrorReply;
import com.seatsync.common.protocol.reply.LoginOkReply;
import com.seatsync.common.protocol.reply.SeatSnapshotReply;
import com.seatsync.common.protocol.request.BookSeatRequest;
import com.seatsync.common.protocol.request.GetSeatsRequest;
import com.seatsync.common.protocol.request.LoginRequest;
import com.seatsync.server.MockRequestDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-socket integration tests for {@link NioBookingServer}.
 * Verifies non-blocking multiplexing with multiple concurrent clients,
 * partial framing, disconnection handling, and session tracking.
 */
class NioBookingServerIntegrationTest {

    private NioBookingServer server;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        // Port 0 triggers dynamic port allocation by OS
        ServerConfig config = ServerConfig.of(ServerConfig.DEFAULT_HOST, 0);
        server = new NioBookingServer(config, new MockRequestDispatcher());
        server.start();
        port = server.getPort();
        assertTrue(port > 0, "Server must bind to a positive dynamic port");
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    private static class TestClient implements AutoCloseable {
        private final Socket socket;
        private final BufferedReader reader;
        private final BufferedWriter writer;

        TestClient(String host, int port) throws IOException {
            this.socket = new Socket(host, port);
            this.socket.setSoTimeout(3000);
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), ProtocolConstants.CHARSET));
            this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), ProtocolConstants.CHARSET));
        }

        void send(String json) throws IOException {
            writer.write(json);
            if (!json.endsWith("\n")) {
                writer.write("\n");
            }
            writer.flush();
        }

        void sendRawBytes(byte[] bytes) throws IOException {
            socket.getOutputStream().write(bytes);
            socket.getOutputStream().flush();
        }

        String readLine() throws IOException {
            return reader.readLine();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    @Test
    @DisplayName("Connect 3 real Socket clients, exchange framed JSON, disconnect one, keep remaining two alive")
    void testThreeClientsConnectExchangeAndDisconnect() throws Exception {
        TestClient client1 = new TestClient("127.0.0.1", port);
        TestClient client2 = new TestClient("127.0.0.1", port);
        TestClient client3 = new TestClient("127.0.0.1", port);

        try {
            // Wait briefly for server accept loop to register all 3
            waitForSessionCount(3, 2000);
            assertEquals(3, server.getSessionRegistry().size());

            // 1. Client 1 sends LOGIN
            LoginRequest login1 = new LoginRequest("req-c1-01", "alice", "pass123");
            client1.send(ProtocolJson.serialize(login1));
            String reply1Str = client1.readLine();
            assertNotNull(reply1Str);
            Message msg1 = ProtocolJson.deserializeMessage(reply1Str);
            assertInstanceOf(LoginOkReply.class, msg1);
            LoginOkReply loginOk1 = (LoginOkReply) msg1;
            assertEquals("req-c1-01", loginOk1.requestId());
            assertEquals("alice", loginOk1.username());

            // 2. Client 2 sends GET_SEATS
            GetSeatsRequest getSeats2 = new GetSeatsRequest("req-c2-01", ProtocolConstants.DEFAULT_SHOW_ID);
            client2.send(ProtocolJson.serialize(getSeats2));
            String reply2Str = client2.readLine();
            assertNotNull(reply2Str);
            Message msg2 = ProtocolJson.deserializeMessage(reply2Str);
            assertInstanceOf(SeatSnapshotReply.class, msg2);
            SeatSnapshotReply snapReply = (SeatSnapshotReply) msg2;
            assertEquals(40, snapReply.seats().size(), "Auditorium must contain 40 seats A1-E8");

            // 3. Client 3 authenticates and books a seat
            LoginRequest login3 = new LoginRequest("req-c3-01", "carol", "secret");
            client3.send(ProtocolJson.serialize(login3));
            String login3Reply = client3.readLine();
            assertInstanceOf(LoginOkReply.class, ProtocolJson.deserializeMessage(login3Reply));

            BookSeatRequest book3 = new BookSeatRequest("req-c3-02", ProtocolConstants.DEFAULT_SHOW_ID, "B3");
            client3.send(ProtocolJson.serialize(book3));
            String book3Reply = client3.readLine();
            assertNotNull(book3Reply);
            Message msg3 = ProtocolJson.deserializeMessage(book3Reply);
            assertInstanceOf(BookOkReply.class, msg3);
            BookOkReply bookOk = (BookOkReply) msg3;
            assertEquals("B3", bookOk.seatId());

            // 4. Disconnect Client 2 intentionally
            client2.close();

            // Wait for server to process EOF and unregister Client 2
            waitForSessionCount(2, 2000);
            assertEquals(2, server.getSessionRegistry().size(), "Session registry should decrement to 2");

            // 5. Verify Client 1 and Client 3 remain alive and fully functional
            GetSeatsRequest getSeats1 = new GetSeatsRequest("req-c1-02", ProtocolConstants.DEFAULT_SHOW_ID);
            client1.send(ProtocolJson.serialize(getSeats1));
            String reply1Again = client1.readLine();
            assertNotNull(reply1Again);
            assertInstanceOf(SeatSnapshotReply.class, ProtocolJson.deserializeMessage(reply1Again));

            GetSeatsRequest getSeats3 = new GetSeatsRequest("req-c3-03", ProtocolConstants.DEFAULT_SHOW_ID);
            client3.send(ProtocolJson.serialize(getSeats3));
            String reply3Again = client3.readLine();
            assertNotNull(reply3Again);
            assertInstanceOf(SeatSnapshotReply.class, ProtocolJson.deserializeMessage(reply3Again));
        } finally {
            try {
                client1.close();
            } catch (Exception ignored) {
            }
            try {
                client2.close();
            } catch (Exception ignored) {
            }
            try {
                client3.close();
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    @DisplayName("Handles partial line fragments split across TCP chunks")
    void testPartialLineHandling() throws Exception {
        TestClient client = new TestClient("127.0.0.1", port);
        try {
            waitForSessionCount(1, 1000);

            LoginRequest login = new LoginRequest("req-frag-01", "dave", "fragmented");
            String fullJson = ProtocolJson.serialize(login) + "\n";
            byte[] bytes = fullJson.getBytes(ProtocolConstants.CHARSET);

            // Send first half
            int mid = bytes.length / 2;
            byte[] firstPart = new byte[mid];
            byte[] secondPart = new byte[bytes.length - mid];
            System.arraycopy(bytes, 0, firstPart, 0, mid);
            System.arraycopy(bytes, mid, secondPart, 0, bytes.length - mid);

            client.sendRawBytes(firstPart);
            // Brief sleep to simulate latency between TCP packets
            Thread.sleep(50);
            client.sendRawBytes(secondPart);

            String reply = client.readLine();
            assertNotNull(reply, "Server must assemble fragmented frames");
            Message msg = ProtocolJson.deserializeMessage(reply);
            assertInstanceOf(LoginOkReply.class, msg);
            assertEquals("req-frag-01", ((LoginOkReply) msg).requestId());
        } finally {
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    @DisplayName("Enforces 16 KiB frame limit and closes abusive connection")
    void testOversizedInputHandling() throws Exception {
        TestClient client = new TestClient("127.0.0.1", port);
        try {
            waitForSessionCount(1, 1000);

            // Create a payload exceeding 16 KiB without newline
            byte[] hugeData = new byte[ProtocolConstants.MAX_FRAME_BYTES + 100];
            for (int i = 0; i < hugeData.length; i++) {
                hugeData[i] = 'A';
            }

            client.sendRawBytes(hugeData);

            // Server should either send PAYLOAD_TOO_LARGE error and/or close connection
            String line = client.readLine();
            if (line != null) {
                Message msg = ProtocolJson.deserializeMessage(line);
                assertInstanceOf(ErrorReply.class, msg);
                assertEquals(ErrorCode.PAYLOAD_TOO_LARGE, ((ErrorReply) msg).errorCode());
            }

            // Client channel should be closed/EOF
            int readAfter = client.socket.getInputStream().read();
            assertEquals(-1, readAfter, "Server must close connection after oversized frame");
        } finally {
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
    }

    private void waitForSessionCount(int expected, long timeoutMs) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (server.getSessionRegistry().size() != expected && (System.currentTimeMillis() - start) < timeoutMs) {
            Thread.sleep(20);
        }
    }
}
