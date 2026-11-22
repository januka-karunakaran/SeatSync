package com.seatsync.network.discovery;

import com.seatsync.common.protocol.ProtocolJson;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Server-side UDP LAN Discovery listener for SeatSync.
 * <p>
 * Listens for {@link DiscoveryRequest} datagrams broadcast by clients on the local subnet.
 * Validates request marker and nonce, applies rate-limiting, and replies with
 * the server's reachable LAN IP, TCP port, TLS requirement, and fingerprint hint.
 * <p>
 * Does NOT transmit passwords, session tokens, or private keys.
 */
public class LanDiscoveryListener implements AutoCloseable {

    private final int requestedPort;
    private final String advertisedIp;
    private final int tcpPort;
    private final boolean tlsRequired;
    private final int tlsPort;
    private final String fingerprint;
    private final DiscoveryRateLimiter rateLimiter;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private DatagramSocket socket;
    private Thread listenerThread;

    public LanDiscoveryListener(
        int requestedPort,
        String manualLanIp,
        int tcpPort,
        boolean tlsRequired,
        int tlsPort,
        String fingerprint,
        DiscoveryRateLimiter rateLimiter
    ) {
        this.requestedPort = requestedPort;
        this.advertisedIp = LanAddressResolver.resolveAdvertisedIp(manualLanIp);
        this.tcpPort = tcpPort;
        this.tlsRequired = tlsRequired;
        this.tlsPort = tlsPort;
        this.fingerprint = fingerprint == null ? "seatsync-v2" : fingerprint;
        this.rateLimiter = rateLimiter == null ? new DiscoveryRateLimiter() : rateLimiter;
    }

    public LanDiscoveryListener(int requestedPort, int tcpPort) {
        this(requestedPort, null, tcpPort, false, 0, "seatsync-v2", null);
    }

    public synchronized void start() throws SocketException {
        if (running.get()) {
            return;
        }

        // Bind to 0.0.0.0 on the requested port to capture broadcast packets
        socket = new DatagramSocket(null);
        socket.setReuseAddress(true);
        socket.setBroadcast(true);
        socket.bind(new InetSocketAddress(requestedPort));

        running.set(true);
        listenerThread = new Thread(this::listenLoop, "seatsync-udp-discovery");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public int getPort() {
        return socket != null ? socket.getLocalPort() : requestedPort;
    }

    public String getAdvertisedIp() {
        return advertisedIp;
    }

    public boolean isRunning() {
        return running.get() && socket != null && !socket.isClosed();
    }

    private void listenLoop() {
        byte[] buffer = new byte[DiscoveryConstants.MAX_DATAGRAM_BYTES];

        while (running.get()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                if (!running.get()) {
                    break;
                }

                if (!rateLimiter.tryAcquire(packet.getAddress())) {
                    // Rate limit exceeded; silently drop packet
                    continue;
                }

                if (packet.getLength() > DiscoveryConstants.MAX_DATAGRAM_BYTES) {
                    continue;
                }

                String json = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8).trim();
                DiscoveryRequest request;
                try {
                    request = ProtocolJson.mapper().readValue(json, DiscoveryRequest.class);
                } catch (Exception e) {
                    // Malformed JSON: drop
                    continue;
                }

                if (request == null || !request.isValid()) {
                    // Bad marker, missing or oversized nonce: drop
                    continue;
                }

                // Construct valid reply
                DiscoveryResponse response = new DiscoveryResponse(
                    DiscoveryConstants.PROTOCOL_MARKER_RESPONSE,
                    request.nonce(),
                    advertisedIp,
                    tcpPort,
                    tlsRequired,
                    tlsPort,
                    fingerprint
                );

                byte[] replyBytes = ProtocolJson.mapper().writeValueAsBytes(response);
                if (replyBytes.length <= DiscoveryConstants.MAX_DATAGRAM_BYTES) {
                    DatagramPacket replyPacket = new DatagramPacket(
                        replyBytes,
                        replyBytes.length,
                        packet.getSocketAddress()
                    );
                    socket.send(replyPacket);
                }
            } catch (SocketException e) {
                // Socket closed during shutdown
                break;
            } catch (IOException ignored) {
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public synchronized void close() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        if (socket != null && !socket.isClosed()) {
            socket.close();
        }

        if (listenerThread != null && Thread.currentThread() != listenerThread) {
            try {
                listenerThread.join(1000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
