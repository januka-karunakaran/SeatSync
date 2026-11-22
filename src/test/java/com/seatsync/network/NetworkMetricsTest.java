package com.seatsync.network;

import com.seatsync.common.model.ErrorCode;
import com.seatsync.common.model.NetworkMetricsSnapshot;
import com.seatsync.common.model.ProtocolConstants;
import com.seatsync.common.model.ServerMetricSnapshot;
import com.seatsync.common.protocol.Message;
import com.seatsync.common.protocol.ProtocolJson;
import com.seatsync.common.protocol.reply.ErrorReply;
import com.seatsync.common.protocol.reply.LoginOkReply;
import com.seatsync.common.protocol.reply.NetworkMetricsReply;
import com.seatsync.common.protocol.request.GetNetworkMetricsRequest;
import com.seatsync.common.protocol.request.GetSeatsRequest;
import com.seatsync.common.protocol.request.LoginRequest;
import com.seatsync.network.metrics.NetworkMetricsTracker;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verification test suite for SeatSync 2.0 NIO Network Metrics instrumentation (PR U10).
 * Covers:
 * - Read-only counters: currently connected, total accepted, closed, bytes in, bytes out, frames
 * - Rejection of oversized frames reflected in telemetry
 * - Event broadcast counting
 * - Connection age and request latency tracking
 * - Thread-safe snapshot generation under heavy concurrent writes
 * - Reset semantics
 * - M4-authorized GET_NETWORK_METRICS request dispatching and access control
 */
class NetworkMetricsTest {

    private NioBookingServer server;
    private NetworkMetricsTracker metricsTracker;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        metricsTracker = new NetworkMetricsTracker();
        MockRequestDispatcher dispatcher = new MockRequestDispatcher(metricsTracker);
        ServerConfig config = ServerConfig.of(ServerConfig.DEFAULT_HOST, 0);
        server = new NioBookingServer(config, dispatcher, metricsTracker);
        dispatcher.setBroadcaster(server.getSessionRegistry()::broadcast);
        server.start();
        port = server.getPort();
        assertTrue(port > 0);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    private static class SimpleClient implements AutoCloseable {
        final Socket socket;
        final BufferedReader reader;
        final BufferedWriter writer;

        SimpleClient(String host, int port) throws IOException {
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

        String readLine() throws IOException {
            return reader.readLine();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    @Test
    @DisplayName("Verify connection lifecycle counters: accepted, currently connected, and closed")
    void testConnectDisconnectCounters() throws Exception {
        assertEquals(0, metricsTracker.getNetworkMetricsSnapshot().totalAccepted());
        assertEquals(0, metricsTracker.getNetworkMetricsSnapshot().currentlyConnected());

        List<SimpleClient> clients = new ArrayList<>();
        try {
            for (int i = 0; i < 4; i++) {
                clients.add(new SimpleClient("127.0.0.1", port));
            }

            waitForCondition(() -> metricsTracker.getNetworkMetricsSnapshot().currentlyConnected() == 4, 2000);
            NetworkMetricsSnapshot snap = metricsTracker.getNetworkMetricsSnapshot();
            assertEquals(4, snap.totalAccepted());
            assertEquals(4, snap.currentlyConnected());
            assertEquals(0, snap.totalClosed());

            // Disconnect 2 clients
            clients.get(0).close();
            clients.get(1).close();

            waitForCondition(() -> metricsTracker.getNetworkMetricsSnapshot().currentlyConnected() == 2, 2000);
            NetworkMetricsSnapshot snapAfterClose = metricsTracker.getNetworkMetricsSnapshot();
            assertEquals(4, snapAfterClose.totalAccepted());
            assertEquals(2, snapAfterClose.currentlyConnected());
            assertEquals(2, snapAfterClose.totalClosed());
            assertTrue(snapAfterClose.averageConnectionAgeMs() >= 0);
        } finally {
            for (SimpleClient c : clients) {
                try {
                    c.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Test
    @DisplayName("Verify bytes and JSON frames counters increment on network traffic")
    void testBytesAndFramesCounting() throws Exception {
        try (SimpleClient client = new SimpleClient("127.0.0.1", port)) {
            waitForCondition(() -> metricsTracker.getNetworkMetricsSnapshot().currentlyConnected() == 1, 1000);

            // Send 3 requests
            for (int i = 0; i < 3; i++) {
                GetSeatsRequest req = new GetSeatsRequest("req-metric-" + i, ProtocolConstants.DEFAULT_SHOW_ID);
                client.send(ProtocolJson.serialize(req));
                String reply = client.readLine();
                assertNotNull(reply);
            }

            NetworkMetricsSnapshot snap = metricsTracker.getNetworkMetricsSnapshot();
            assertTrue(snap.bytesIn() > 0, "bytesIn should reflect client request bytes");
            assertTrue(snap.bytesOut() > 0, "bytesOut should reflect server reply bytes");
            assertTrue(snap.jsonFrames() >= 3, "jsonFrames should count parsed frames");
        }
    }

    @Test
    @DisplayName("Verify rejected oversized frames increment telemetry counter")
    void testRejectedOversizedFrames() throws Exception {
        try (SimpleClient client = new SimpleClient("127.0.0.1", port)) {
            waitForCondition(() -> metricsTracker.getNetworkMetricsSnapshot().currentlyConnected() == 1, 1000);

            byte[] huge = new byte[ProtocolConstants.MAX_FRAME_BYTES + 50];
            for (int i = 0; i < huge.length; i++) {
                huge[i] = 'Z';
            }
            client.socket.getOutputStream().write(huge);
            client.socket.getOutputStream().flush();

            String reply = client.readLine();
            assertNotNull(reply);

            waitForCondition(() -> metricsTracker.getNetworkMetricsSnapshot().rejectedOversizedFrames() == 1, 1000);
            assertEquals(1, metricsTracker.getNetworkMetricsSnapshot().rejectedOversizedFrames());
        }
    }

    @Test
    @DisplayName("Verify event broadcast counter increments on server-side broadcasts")
    void testEventBroadcastCounter() {
        assertEquals(0, metricsTracker.getNetworkMetricsSnapshot().eventBroadcastCount());
        server.getSessionRegistry().broadcast("{\"type\":\"TEST_EVENT\"}\n");
        assertEquals(1, metricsTracker.getNetworkMetricsSnapshot().eventBroadcastCount());
    }

    @Test
    @DisplayName("Verify reset semantics clear all telemetry counters")
    void testResetSemantics() {
        metricsTracker.recordAcceptedConnection();
        metricsTracker.recordBytesIn(500);
        metricsTracker.recordBytesOut(1000);
        metricsTracker.recordJsonFrame();
        metricsTracker.recordRejectedFrame();
        metricsTracker.recordBroadcast();

        NetworkMetricsSnapshot before = metricsTracker.getNetworkMetricsSnapshot();
        assertEquals(1, before.totalAccepted());
        assertEquals(500, before.bytesIn());

        metricsTracker.reset();

        NetworkMetricsSnapshot after = metricsTracker.getNetworkMetricsSnapshot();
        assertEquals(0, after.totalAccepted());
        assertEquals(0, after.currentlyConnected());
        assertEquals(0, after.bytesIn());
        assertEquals(0, after.bytesOut());
        assertEquals(0, after.jsonFrames());
        assertEquals(0, after.rejectedOversizedFrames());
        assertEquals(0, after.eventBroadcastCount());
    }

    @Test
    @DisplayName("Concurrent writes across multiple worker threads produce accurate, thread-safe snapshots")
    void testConcurrentWritesAndSnapshotIntegrity() throws Exception {
        int threads = 16;
        int operationsPerThread = 500;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        metricsTracker.recordBytesIn(10);
                        metricsTracker.recordBytesOut(20);
                        metricsTracker.recordJsonFrame();
                        metricsTracker.recordRequest("TEST", 1_000_000L, true);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // Concurrent reader taking snapshots
        AtomicBoolean reading = new AtomicBoolean(true);
        Thread reader = new Thread(() -> {
            while (reading.get()) {
                NetworkMetricsSnapshot snap = metricsTracker.getNetworkMetricsSnapshot();
                assertNotNull(snap);
                assertTrue(snap.bytesIn() >= 0);
            }
        });
        reader.start();

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        reading.set(false);
        reader.join(1000);
        pool.shutdown();

        NetworkMetricsSnapshot finalSnap = metricsTracker.getNetworkMetricsSnapshot();
        long expectedOps = (long) threads * operationsPerThread;
        assertEquals(expectedOps * 10, finalSnap.bytesIn());
        assertEquals(expectedOps * 20, finalSnap.bytesOut());
        assertEquals(expectedOps, finalSnap.jsonFrames());
        assertTrue(finalSnap.averageLatencyMs() > 0.0);
    }

    @Test
    @DisplayName("M4-authorized GET_NETWORK_METRICS request: rejects unauthenticated or non-admin, succeeds for ADMIN")
    void testAuthorizedNetworkMetricsRequest() throws Exception {
        try (SimpleClient client = new SimpleClient("127.0.0.1", port)) {
            waitForCondition(() -> metricsTracker.getNetworkMetricsSnapshot().currentlyConnected() == 1, 1000);

            // 1. Unauthenticated request -> AUTH_REQUIRED
            GetNetworkMetricsRequest unauthReq = new GetNetworkMetricsRequest("req-net-01");
            client.send(ProtocolJson.serialize(unauthReq));
            String rep1Str = client.readLine();
            Message rep1 = ProtocolJson.deserializeMessage(rep1Str);
            assertInstanceOf(ErrorReply.class, rep1);
            assertEquals(ErrorCode.AUTH_REQUIRED, ((ErrorReply) rep1).errorCode());

            // 2. Regular user login (alice) -> FORBIDDEN_ROLE
            LoginRequest loginUser = new LoginRequest("req-login-01", "alice", "pass");
            client.send(ProtocolJson.serialize(loginUser));
            client.readLine(); // login ok

            client.send(ProtocolJson.serialize(unauthReq));
            String rep2Str = client.readLine();
            Message rep2 = ProtocolJson.deserializeMessage(rep2Str);
            assertInstanceOf(ErrorReply.class, rep2);
            assertEquals(ErrorCode.FORBIDDEN_ROLE, ((ErrorReply) rep2).errorCode());

            // 3. Admin user login (admin-root) -> NETWORK_METRICS_REPLY
            LoginRequest loginAdmin = new LoginRequest("req-login-02", "admin-root", "secret");
            client.send(ProtocolJson.serialize(loginAdmin));
            client.readLine(); // login ok

            client.send(ProtocolJson.serialize(new GetNetworkMetricsRequest("req-net-admin")));
            String rep3Str = client.readLine();
            Message rep3 = ProtocolJson.deserializeMessage(rep3Str);
            assertInstanceOf(NetworkMetricsReply.class, rep3);
            NetworkMetricsReply netReply = (NetworkMetricsReply) rep3;
            assertEquals("req-net-admin", netReply.requestId());
            assertNotNull(netReply.metrics());
            assertTrue(netReply.metrics().bytesIn() > 0);
            assertTrue(netReply.metrics().jsonFrames() > 0);
        }
    }

    private void waitForCondition(ConditionChecker condition, long timeoutMs) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (!condition.check() && (System.currentTimeMillis() - start) < timeoutMs) {
            Thread.sleep(20);
        }
    }

    @FunctionalInterface
    private interface ConditionChecker {
        boolean check();
    }
}
