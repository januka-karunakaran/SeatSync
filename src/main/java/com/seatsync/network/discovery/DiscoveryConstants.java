package com.seatsync.network.discovery;

/**
 * Constants governing the UDP LAN Discovery protocol for SeatSync 2.0.
 * <p>
 * NOTE ON ROUTING BOUNDARIES: UDP discovery relies on IPv4 subnet broadcast
 * (e.g. 255.255.255.255 or subnet-directed broadcast). Under standard network
 * topology, routers do NOT forward local broadcasts across subnets or across
 * the public internet. Consequently, discovery is strictly supported within
 * the same Local Area Network (LAN) broadcast domain.
 */
public final class DiscoveryConstants {

    private DiscoveryConstants() {
        // utility class
    }

    /**
     * Bounded protocol marker identifying a SeatSync discovery probe request.
     */
    public static final String PROTOCOL_MARKER_REQUEST = "SEATSYNC_DISCOVER_V2";

    /**
     * Bounded protocol marker identifying a SeatSync discovery reply.
     */
    public static final String PROTOCOL_MARKER_RESPONSE = "SEATSYNC_DISCOVER_RESP_V2";

    /**
     * Default UDP discovery port (5051). Independent of default TCP port.
     */
    public static final int DEFAULT_UDP_PORT = 5051;

    /**
     * Maximum allowed datagram payload in bytes. Well below standard 576/1500-byte MTU.
     */
    public static final int MAX_DATAGRAM_BYTES = 512;

    /**
     * Maximum allowed length for probe correlation nonce.
     */
    public static final int MAX_NONCE_LENGTH = 64;

    /**
     * Default probe timeout in milliseconds.
     */
    public static final int DEFAULT_TIMEOUT_MS = 2000;

    /**
     * Maximum discovery responses allowed per second per IP (rate-limiting defense against amplification).
     */
    public static final int MAX_REPLIES_PER_SEC_PER_IP = 25;
}
