package com.seatsync.network.discovery;

import com.seatsync.common.protocol.ProtocolJson;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Client-side UDP discovery probe and standalone CLI utility.
 * <p>
 * Emits a bounded {@link DiscoveryRequest} datagram containing a unique nonce
 * to the broadcast address or specific host, and awaits matching {@link DiscoveryResponse}
 * replies within a configurable timeout.
 * <p>
 * CLI Usage:
 * <pre>
 *   java -cp seatsync.jar com.seatsync.network.discovery.UdpDiscoveryProbe --port 5051 --timeout 2000
 * </pre>
 * <p>
 * MANUAL SAME-LAN TEST INSTRUCTIONS:
 * 1. Start SeatSync server on Machine A (e.g. 192.168.1.100).
 * 2. On Machine B on the same Wi-Fi/Ethernet subnet, execute:
 *    java -cp target/seatsync-1.0.0-SNAPSHOT.jar com.seatsync.network.discovery.UdpDiscoveryProbe
 * 3. Machine B broadcasts to 255.255.255.255:5051, Machine A responds, and Machine B displays:
 *    Discovered SeatSync server at 192.168.1.100:5050.
 * 4. NOTE: Routers do NOT forward UDP broadcast packets across different subnets or the internet.
 *    Discovery is strictly supported within the same broadcast domain.
 */
public class UdpDiscoveryProbe {

    /**
     * Probes for SeatSync servers on the specified broadcast or unicast host and UDP port.
     *
     * @param targetHost Target host or broadcast address (e.g. 255.255.255.255, 127.0.0.1)
     * @param udpPort    Target UDP port
     * @param timeoutMs  Socket read timeout in milliseconds
     * @return List of verified {@link DiscoveryResponse} instances matching the emitted nonce
     * @throws IOException if network transmission fails
     */
    public static List<DiscoveryResponse> probe(String targetHost, int udpPort, int timeoutMs) throws IOException {
        Objects.requireNonNull(targetHost, "targetHost must not be null");

        String nonce = UUID.randomUUID().toString();
        DiscoveryRequest request = new DiscoveryRequest(DiscoveryConstants.PROTOCOL_MARKER_REQUEST, nonce);
        byte[] requestBytes = ProtocolJson.mapper().writeValueAsBytes(request);

        List<DiscoveryResponse> responses = new ArrayList<>();

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(Math.max(timeoutMs, 100));

            InetAddress targetAddr = InetAddress.getByName(targetHost);
            DatagramPacket sendPacket = new DatagramPacket(
                requestBytes,
                requestBytes.length,
                targetAddr,
                udpPort
            );
            socket.send(sendPacket);

            byte[] recvBuffer = new byte[DiscoveryConstants.MAX_DATAGRAM_BYTES];
            long deadline = System.currentTimeMillis() + timeoutMs;

            while (System.currentTimeMillis() < deadline) {
                try {
                    DatagramPacket recvPacket = new DatagramPacket(recvBuffer, recvBuffer.length);
                    socket.receive(recvPacket);

                    String json = new String(
                        recvPacket.getData(),
                        recvPacket.getOffset(),
                        recvPacket.getLength(),
                        StandardCharsets.UTF_8
                    ).trim();

                    DiscoveryResponse response = ProtocolJson.mapper().readValue(json, DiscoveryResponse.class);
                    if (response != null && response.isValidFor(nonce)) {
                        responses.add(response);
                    }
                } catch (SocketTimeoutException e) {
                    // Timeout reached, normal exit
                    break;
                } catch (Exception ignored) {
                    // Ignore malformed responses from third-party packets
                }
            }
        }

        return responses;
    }

    /**
     * Convenience method to broadcast to 255.255.255.255 on the default port.
     */
    public static List<DiscoveryResponse> discoverAll(int udpPort, int timeoutMs) throws IOException {
        return probe("255.255.255.255", udpPort, timeoutMs);
    }

    /**
     * CLI entry point for command-line probe testing.
     *
     * @param args CLI arguments: [--port 5051] [--timeout 2000] [--host 255.255.255.255]
     */
    public static void main(String[] args) {
        int port = DiscoveryConstants.DEFAULT_UDP_PORT;
        int timeout = DiscoveryConstants.DEFAULT_TIMEOUT_MS;
        String host = "255.255.255.255";

        for (int i = 0; i < args.length; i++) {
            if ("--port".equals(args[i]) && i + 1 < args.length) {
                port = Integer.parseInt(args[++i]);
            } else if ("--timeout".equals(args[i]) && i + 1 < args.length) {
                timeout = Integer.parseInt(args[++i]);
            } else if ("--host".equals(args[i]) && i + 1 < args.length) {
                host = args[++i];
            }
        }

        System.out.println("=================================================");
        System.out.println("  SeatSync 2.0 UDP LAN Discovery CLI Probe");
        System.out.println("=================================================");
        System.out.println("Target: " + host + ":" + port + " (Timeout: " + timeout + "ms)");
        System.out.println("Emitting discovery probe with secure nonce...");

        try {
            List<DiscoveryResponse> discovered = probe(host, port, timeout);

            if (discovered.isEmpty()) {
                System.out.println("[!] No SeatSync servers discovered within timeout.");
                System.out.println("    Verify that server is running on the same LAN subnet");
                System.out.println("    and that UDP port " + port + " is not blocked by local firewall.");
            } else {
                System.out.println("[+] Discovered " + discovered.size() + " active SeatSync server(s):");
                for (int i = 0; i < discovered.size(); i++) {
                    DiscoveryResponse resp = discovered.get(i);
                    System.out.printf(
                        "  [%d] IP: %-15s | TCP Port: %-5d | TLS Required: %-5b | TLS Port: %-5d | Fingerprint: %s%n",
                        i + 1,
                        resp.serverIp(),
                        resp.tcpPort(),
                        resp.tlsRequired(),
                        resp.tlsPort(),
                        resp.fingerprint()
                    );
                }
            }
        } catch (Exception e) {
            System.err.println("[-] Probe execution failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
