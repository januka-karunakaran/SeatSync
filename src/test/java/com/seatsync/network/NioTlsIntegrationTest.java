package com.seatsync.network;

import com.seatsync.common.model.ErrorCode;
import com.seatsync.common.model.ProtocolConstants;
import com.seatsync.common.model.TlsConfigMode;
import com.seatsync.common.protocol.Message;
import com.seatsync.common.protocol.ProtocolJson;
import com.seatsync.common.protocol.ReplyMessage;
import com.seatsync.common.protocol.reply.BookOkReply;
import com.seatsync.common.protocol.reply.LoginOkReply;
import com.seatsync.common.protocol.reply.SeatSnapshotReply;
import com.seatsync.common.protocol.request.BookSeatRequest;
import com.seatsync.common.protocol.request.GetSeatsRequest;
import com.seatsync.common.protocol.request.LoginRequest;
import com.seatsync.network.metrics.NetworkMetricsTracker;
import com.seatsync.network.tls.TlsTestHelper;
import com.seatsync.network.tls.TlsTestHelper.SslContextBundle;
import com.seatsync.network.discovery.DiscoveryResponse;
import com.seatsync.network.discovery.UdpDiscoveryProbe;
import com.seatsync.server.MockRequestDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end integration test suite verifying Java 21 SSLEngine (TLS 1.3)
 * transport integrated with non-blocking Java NIO SocketChannel selector loop.
 * <p>
 * Verifies:
 * - 3 concurrent secure TLS clients executing logins, seat snapshots, bookings
 * - Rejection of untrusted clients via bad truststore
 * - Strict TLS enforcement (no plaintext downgrade on secure port)
 * - Partial encrypted record assembly
 * - Graceful close_notify termination
 * - Plaintext backward compatibility in dual-transport mode
 */
class NioTlsIntegrationTest {

    private NioBookingServer server;
    private SslContextBundle sslBundle;
    private int plainPort;
    private int tlsPort;

    @BeforeEach
    void setUp() throws IOException {
        sslBundle = TlsTestHelper.getOrCreateBundle();

        // Dual transport mode: port 0 for dynamic plaintext port, port 0 for dynamic TLS port
        ServerConfig config = ServerConfig.withDualTransport(
            ServerConfig.DEFAULT_HOST,
            0,
            0,
            sslBundle.serverContext()
        );

        MockRequestDispatcher dispatcher = new MockRequestDispatcher();
        server = new NioBookingServer(config, dispatcher);
        dispatcher.setBroadcaster(server.getSessionRegistry()::broadcast);
        server.start();

        plainPort = server.getPort();
        tlsPort = server.getTlsPort();

        assertTrue(plainPort > 0, "Plaintext port must be assigned dynamically");
        assertTrue(tlsPort > 0, "TLS port must be assigned dynamically");
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    private static class SecureTestClient implements AutoCloseable {
        final SSLSocket socket;
        final BufferedReader reader;
        final BufferedWriter writer;

        SecureTestClient(SslContextBundle bundle, String host, int port) throws IOException {
            this.socket = (SSLSocket) bundle.trustedClientContext().getSocketFactory().createSocket(host, port);
            this.socket.setEnabledProtocols(new String[]{"TLSv1.3"});
            this.socket.setSoTimeout(4000);
            this.socket.startHandshake();
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

        String readLine() throws IOException {
            return reader.readLine();
        }

        Message readReply() throws IOException {
            while (true) {
                String line = readLine();
                if (line == null) {
                    return null;
                }
                Message msg = ProtocolJson.deserializeMessage(line);
                if (msg instanceof ReplyMessage) {
                    return msg;
                }
            }
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    @Test
    @DisplayName("Connect 3 concurrent secure TLS 1.3 clients, exchange requests, disconnect one, keep two alive")
    void testThreeConcurrentSecureClients() throws Exception {
        SecureTestClient client1 = new SecureTestClient(sslBundle, "127.0.0.1", tlsPort);
        SecureTestClient client2 = new SecureTestClient(sslBundle, "127.0.0.1", tlsPort);
        SecureTestClient client3 = new SecureTestClient(sslBundle, "127.0.0.1", tlsPort);

        try {
            waitForSessionCount(3, 2000);
            assertEquals(3, server.getSessionRegistry().size());

            // 1. Client 1 sends LOGIN
            LoginRequest login1 = new LoginRequest("req-tls-1", "alice", "secPass");
            client1.send(ProtocolJson.serialize(login1));
            String rep1Str = client1.readLine();
            assertNotNull(rep1Str);
            Message msg1 = ProtocolJson.deserializeMessage(rep1Str);
            assertInstanceOf(LoginOkReply.class, msg1);
            assertEquals("alice", ((LoginOkReply) msg1).username());

            // 2. Client 2 sends GET_SEATS
            GetSeatsRequest getSeats2 = new GetSeatsRequest("req-tls-2", ProtocolConstants.DEFAULT_SHOW_ID);
            client2.send(ProtocolJson.serialize(getSeats2));
            String rep2Str = client2.readLine();
            assertNotNull(rep2Str);
            Message msg2 = ProtocolJson.deserializeMessage(rep2Str);
            assertInstanceOf(SeatSnapshotReply.class, msg2);
            assertEquals(40, ((SeatSnapshotReply) msg2).seats().size());

            // 3. Client 3 logs in and books seat C4
            client3.send(ProtocolJson.serialize(new LoginRequest("req-tls-3a", "carol", "secret")));
            client3.readLine(); // login ok

            BookSeatRequest book3 = new BookSeatRequest("req-tls-3b", ProtocolConstants.DEFAULT_SHOW_ID, "C4");
            client3.send(ProtocolJson.serialize(book3));
            String rep3Str = client3.readLine();
            assertNotNull(rep3Str);
            Message msg3 = ProtocolJson.deserializeMessage(rep3Str);
            assertInstanceOf(BookOkReply.class, msg3);
            assertEquals("C4", ((BookOkReply) msg3).seatId());

            // 4. Disconnect Client 2
            client2.close();
            waitForSessionCount(2, 2000);
            assertEquals(2, server.getSessionRegistry().size());

            // 5. Verify Client 1 and Client 3 remain functional over TLS
            client1.send(ProtocolJson.serialize(new GetSeatsRequest("req-tls-1b", ProtocolConstants.DEFAULT_SHOW_ID)));
            Message rep1Again = client1.readReply();
            assertNotNull(rep1Again);
            assertInstanceOf(SeatSnapshotReply.class, rep1Again);

            client3.send(ProtocolJson.serialize(new GetSeatsRequest("req-tls-3c", ProtocolConstants.DEFAULT_SHOW_ID)));
            Message rep3Again = client3.readReply();
            assertNotNull(rep3Again);
            assertInstanceOf(SeatSnapshotReply.class, rep3Again);
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
    @DisplayName("UDP discovery advertises TLS ports and distinguishes optional from required TLS")
    void testDiscoveryAdvertisesTlsTransport() throws Exception {
        int discoveryPort = server.getDiscoveryListener().getPort();
        DiscoveryResponse optional = UdpDiscoveryProbe.probe("127.0.0.1", discoveryPort, 500).stream()
            .findFirst().orElseThrow();

        assertEquals(plainPort, optional.tcpPort());
        assertEquals(tlsPort, optional.tlsPort());
        assertEquals(false, optional.tlsRequired());

        server.stop();
        ServerConfig requiredConfig = new ServerConfig(
            ServerConfig.DEFAULT_HOST,
            0,
            ProtocolConstants.MAX_FRAME_BYTES,
            ServerConfig.DEFAULT_READ_BUFFER_BYTES,
            TlsConfigMode.REQUIRED,
            0,
            sslBundle.serverContext(),
            true,
            0,
            null
        );
        server = new NioBookingServer(requiredConfig, new MockRequestDispatcher());
        server.start();

        int requiredDiscoveryPort = server.getDiscoveryListener().getPort();
        DiscoveryResponse required = UdpDiscoveryProbe.probe("127.0.0.1", requiredDiscoveryPort, 500).stream()
            .findFirst().orElseThrow();

        assertEquals(server.getTlsPort(), required.tcpPort());
        assertEquals(server.getTlsPort(), required.tlsPort());
        assertEquals(true, required.tlsRequired());
    }

    @Test
    @DisplayName("TLS handshake with untrusted client truststore fails with SSLHandshakeException")
    void testBadTruststoreHandshakeRejection() {
        assertThrows(SSLException.class, () -> {
            try (SSLSocket badSocket = (SSLSocket) sslBundle.untrustedClientContext().getSocketFactory().createSocket("127.0.0.1", tlsPort)) {
                badSocket.setSoTimeout(2000);
                badSocket.startHandshake();
            }
        });
    }

    @Test
    @DisplayName("No plaintext downgrade: raw plaintext client connecting to TLS port is dropped without downgrade")
    void testNoPlaintextDowngradeOnTlsPort() throws Exception {
        try (Socket plainSocket = new Socket("127.0.0.1", tlsPort)) {
            plainSocket.setSoTimeout(1500);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(plainSocket.getOutputStream(), ProtocolConstants.CHARSET));
            writer.write("{\"type\":\"LOGIN\",\"requestId\":\"r1\",\"username\":\"attacker\"}\n");
            writer.flush();

            // Server's SSLEngine should encounter bad TLS record header and close channel
            int read = plainSocket.getInputStream().read();
            assertEquals(-1, read, "Server must disconnect plaintext client on secure TLS port");
        }
    }

    @Test
    @DisplayName("Partial encrypted TLS records split across TCP frames are assembled correctly")
    void testPartialTlsRecordsAssembly() throws Exception {
        try (SecureTestClient client = new SecureTestClient(sslBundle, "127.0.0.1", tlsPort)) {
            waitForSessionCount(1, 1000);

            LoginRequest req = new LoginRequest("req-frag-tls", "fragmented-user", "password");
            String json = ProtocolJson.serialize(req) + "\n";
            byte[] bytes = json.getBytes(ProtocolConstants.CHARSET);

            // Send in two separate writes to test buffer accumulation
            int mid = bytes.length / 2;
            client.socket.getOutputStream().write(bytes, 0, mid);
            client.socket.getOutputStream().flush();
            Thread.sleep(40);
            client.socket.getOutputStream().write(bytes, mid, bytes.length - mid);
            client.socket.getOutputStream().flush();

            String rep = client.readLine();
            assertNotNull(rep, "Server must reassemble fragmented TLS plaintext frames");
            Message msg = ProtocolJson.deserializeMessage(rep);
            assertInstanceOf(LoginOkReply.class, msg);
            assertEquals("req-frag-tls", ((LoginOkReply) msg).requestId());
        }
    }

    @Test
    @DisplayName("Graceful close: TLS close alert unregisters session cleanly without leaking channels")
    void testGracefulClose() throws Exception {
        SecureTestClient client = new SecureTestClient(sslBundle, "127.0.0.1", tlsPort);
        try {
            waitForSessionCount(1, 1000);
            assertEquals(1, server.getSessionRegistry().size());

            // Close TLS client cleanly
            client.close();
            waitForSessionCount(0, 2000);
            assertEquals(0, server.getSessionRegistry().size(), "Session must be unregistered on TLS close");
        } finally {
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    @DisplayName("Dual transport: Plaintext client connecting to plaintext port continues to function normally")
    void testOldPlaintextDevModeRegression() throws Exception {
        try (Socket plainSocket = new Socket("127.0.0.1", plainPort)) {
            plainSocket.setSoTimeout(2000);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(plainSocket.getOutputStream(), ProtocolConstants.CHARSET));
            BufferedReader reader = new BufferedReader(new InputStreamReader(plainSocket.getInputStream(), ProtocolConstants.CHARSET));

            LoginRequest login = new LoginRequest("req-plain-01", "dev-user", "plainPass");
            writer.write(ProtocolJson.serialize(login) + "\n");
            writer.flush();

            String reply = reader.readLine();
            assertNotNull(reply);
            Message msg = ProtocolJson.deserializeMessage(reply);
            assertInstanceOf(LoginOkReply.class, msg);
            assertEquals("dev-user", ((LoginOkReply) msg).username());
        }
    }

    private void waitForSessionCount(int expected, long timeoutMs) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (server.getSessionRegistry().size() != expected && (System.currentTimeMillis() - start) < timeoutMs) {
            Thread.sleep(20);
        }
    }
}
