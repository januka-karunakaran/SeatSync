package com.seatsync.network;

import com.seatsync.common.model.ProtocolConstants;
import com.seatsync.common.protocol.ProtocolJson;
import com.seatsync.network.discovery.DiscoveryConstants;
import com.seatsync.network.discovery.DiscoveryRateLimiter;
import com.seatsync.network.discovery.DiscoveryRequest;
import com.seatsync.network.discovery.DiscoveryResponse;
import com.seatsync.network.discovery.LanAddressResolver;
import com.seatsync.network.discovery.LanDiscoveryListener;
import com.seatsync.network.discovery.UdpDiscoveryProbe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verification test suite for SeatSync 2.0 UDP LAN Discovery.
 * Covers:
 * - Valid request/reply datagram exchange with correlation nonce
 * - Rejection of invalid markers and malformed/oversized nonces
 * - Probe timeout semantics when no server is present
 * - Clean listener shutdown and socket cleanup
 * - LAN IP resolution and manual override (never 0.0.0.0)
 * - Rate limiting against datagram amplification
 * - Integrated discovery lifecycle with NioBookingServer
 */
class UdpDiscoveryTest {

    private LanDiscoveryListener listener;
    private int udpPort;

    @BeforeEach
    void setUp() throws IOException {
        // Port 0 selects dynamic ephemeral UDP port to prevent port conflicts
        listener = new LanDiscoveryListener(0, 5050);
        listener.start();
        udpPort = listener.getPort();
        assertTrue(udpPort > 0, "Listener must bind to an active dynamic UDP port");
        assertTrue(listener.isRunning());
    }

    @AfterEach
    void tearDown() {
        if (listener != null) {
            listener.close();
        }
    }

    @Test
    @DisplayName("Valid probe receives matching nonce, valid reachable LAN IP, and correct TCP port")
    void testValidDiscoveryRequestAndReply() throws Exception {
        List<DiscoveryResponse> results = UdpDiscoveryProbe.probe("127.0.0.1", udpPort, 1500);

        assertEquals(1, results.size(), "Should discover exactly 1 server on loopback target");
        DiscoveryResponse response = results.get(0);

        assertEquals(DiscoveryConstants.PROTOCOL_MARKER_RESPONSE, response.marker());
        assertEquals(5050, response.tcpPort());
        assertFalse("0.0.0.0".equals(response.serverIp()), "Server must not advertise 0.0.0.0");
        assertNotNull(response.serverIp());
        assertFalse(response.tlsRequired());
        assertEquals("seatsync-v2", response.fingerprint());
    }

    @Test
    @DisplayName("Request with bad protocol marker is dropped and ignored")
    void testBadProtocolMarkerIgnored() throws Exception {
        String badRequestJson = "{\"marker\":\"WRONG_MARKER_V1\",\"nonce\":\"test-nonce-123\"}";
        byte[] payload = badRequestJson.getBytes(StandardCharsets.UTF_8);

        try (DatagramSocket clientSocket = new DatagramSocket()) {
            clientSocket.setSoTimeout(500);
            DatagramPacket packet = new DatagramPacket(
                payload,
                payload.length,
                InetAddress.getByName("127.0.0.1"),
                udpPort
            );
            clientSocket.send(packet);

            byte[] recvBuffer = new byte[DiscoveryConstants.MAX_DATAGRAM_BYTES];
            DatagramPacket recvPacket = new DatagramPacket(recvBuffer, recvBuffer.length);

            // Server must drop invalid marker without replying
            boolean timedOut = false;
            try {
                clientSocket.receive(recvPacket);
            } catch (SocketTimeoutException e) {
                timedOut = true;
            }
            assertTrue(timedOut, "Server must not reply to invalid protocol marker");
        }
    }

    @Test
    @DisplayName("Request with empty or oversized nonce is dropped")
    void testBadOrEmptyNonceIgnored() throws Exception {
        // 1. Empty nonce
        String emptyNonceJson = "{\"marker\":\"" + DiscoveryConstants.PROTOCOL_MARKER_REQUEST + "\",\"nonce\":\"\"}";
        byte[] payloadEmpty = emptyNonceJson.getBytes(StandardCharsets.UTF_8);

        try (DatagramSocket clientSocket = new DatagramSocket()) {
            clientSocket.setSoTimeout(500);
            clientSocket.send(new DatagramPacket(
                payloadEmpty,
                payloadEmpty.length,
                InetAddress.getByName("127.0.0.1"),
                udpPort
            ));

            boolean timedOut = false;
            try {
                byte[] recvBuffer = new byte[DiscoveryConstants.MAX_DATAGRAM_BYTES];
                clientSocket.receive(new DatagramPacket(recvBuffer, recvBuffer.length));
            } catch (SocketTimeoutException e) {
                timedOut = true;
            }
            assertTrue(timedOut, "Server must drop empty nonce");
        }

        // 2. Oversized nonce (> 64 characters)
        String hugeNonce = "a".repeat(100);
        String hugeNonceJson = "{\"marker\":\"" + DiscoveryConstants.PROTOCOL_MARKER_REQUEST + "\",\"nonce\":\"" + hugeNonce + "\"}";
        byte[] payloadHuge = hugeNonceJson.getBytes(StandardCharsets.UTF_8);

        try (DatagramSocket clientSocket = new DatagramSocket()) {
            clientSocket.setSoTimeout(500);
            clientSocket.send(new DatagramPacket(
                payloadHuge,
                payloadHuge.length,
                InetAddress.getByName("127.0.0.1"),
                udpPort
            ));

            boolean timedOut = false;
            try {
                byte[] recvBuffer = new byte[DiscoveryConstants.MAX_DATAGRAM_BYTES];
                clientSocket.receive(new DatagramPacket(recvBuffer, recvBuffer.length));
            } catch (SocketTimeoutException e) {
                timedOut = true;
            }
            assertTrue(timedOut, "Server must drop oversized nonce");
        }
    }

    @Test
    @DisplayName("Probe times out cleanly when no server is listening")
    void testProbeTimeoutWhenNoServerListening() throws Exception {
        // Probe an unassigned high port
        List<DiscoveryResponse> results = UdpDiscoveryProbe.probe("127.0.0.1", 59999, 400);
        assertTrue(results.isEmpty(), "Probe on unassigned port must return empty list upon timeout");
    }

    @Test
    @DisplayName("Clean listener shutdown stops listener thread and closes socket")
    void testCleanShutdown() {
        assertTrue(listener.isRunning());
        listener.close();
        assertFalse(listener.isRunning(), "Listener must report not running after close");

        // Second close should be idempotent
        listener.close();
        assertFalse(listener.isRunning());
    }

    @Test
    @DisplayName("LAN address resolver respects manual IP override and avoids 0.0.0.0")
    void testReachableLanIpResolution() {
        // Manual override
        String manual = LanAddressResolver.resolveAdvertisedIp("192.168.1.200");
        assertEquals("192.168.1.200", manual);

        // 0.0.0.0 override rejected, resolves to reachable interface or loopback
        String autoFromZero = LanAddressResolver.resolveAdvertisedIp("0.0.0.0");
        assertNotEquals("0.0.0.0", autoFromZero);
        assertNotNull(autoFromZero);

        // Blank/null auto-detection
        String autoDetected = LanAddressResolver.resolveAdvertisedIp(null);
        assertNotNull(autoDetected);
        assertNotEquals("0.0.0.0", autoDetected);
    }

    @Test
    @DisplayName("Rate limiter throttles excessive discovery requests from the same IP")
    void testRateLimiterThrottling() throws Exception {
        DiscoveryRateLimiter limiter = new DiscoveryRateLimiter(5);
        InetAddress localhost = InetAddress.getByName("127.0.0.1");

        // First 5 should succeed
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire(localhost), "Request " + (i + 1) + " should be allowed");
        }

        // 6th request within same second must be rejected
        assertFalse(limiter.tryAcquire(localhost), "6th request should be throttled");
    }

    @Test
    @DisplayName("NioBookingServer automatically coordinates with UDP Discovery listener")
    void testServerIntegrationWithDiscovery() throws Exception {
        // Configure server with dynamic TCP port and dynamic UDP port (port 0)
        ServerConfig config = new ServerConfig(
            ServerConfig.DEFAULT_HOST,
            0,
            ProtocolConstants.MAX_FRAME_BYTES,
            ServerConfig.DEFAULT_READ_BUFFER_BYTES,
            true,
            0,
            null
        );

        NioBookingServer server = new NioBookingServer(config);
        LanDiscoveryListener serverDiscovery = null;
        try {
            server.start();
            assertTrue(server.isRunning());
            serverDiscovery = server.getDiscoveryListener();
            assertNotNull(serverDiscovery, "Server must instantiate discovery listener");
            assertTrue(serverDiscovery.isRunning());

            int serverTcpPort = server.getPort();
            int serverUdpPort = serverDiscovery.getPort();
            assertTrue(serverTcpPort > 0);
            assertTrue(serverUdpPort > 0);

            // Probe server's UDP port
            List<DiscoveryResponse> responses = UdpDiscoveryProbe.probe("127.0.0.1", serverUdpPort, 1500);
            assertEquals(1, responses.size());
            assertEquals(serverTcpPort, responses.get(0).tcpPort(), "Discovered TCP port must match bound server port");
        } finally {
            server.stop();
            assertFalse(server.isRunning());
            assertNotNull(serverDiscovery);
            assertFalse(serverDiscovery.isRunning(), "Server stop must also stop discovery listener");
        }
    }
}
