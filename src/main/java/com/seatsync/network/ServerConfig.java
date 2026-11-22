package com.seatsync.network;

import com.seatsync.common.model.ProtocolConstants;
import com.seatsync.network.discovery.DiscoveryConstants;

import java.util.Objects;

/**
 * Immutable configuration for the non-blocking Java NIO booking server and UDP discovery.
 *
 * @param host                Listening host address (default: 127.0.0.1)
 * @param port                Listening TCP port (default: 8080, 0 for dynamic ephemeral allocation)
 * @param maxFrameBytes       Maximum allowable line frame size in bytes (default: 16 KiB)
 * @param readBufferBytes     Direct/Heap buffer size allocated per read operation
 * @param udpDiscoveryEnabled Whether the UDP discovery listener should start with the server
 * @param udpPort             Listening UDP port for discovery (default: 5051)
 * @param manualLanIp         Optional manual LAN IP to advertise instead of auto-detection
 */
public record ServerConfig(
    String host,
    int port,
    int maxFrameBytes,
    int readBufferBytes,
    boolean udpDiscoveryEnabled,
    int udpPort,
    String manualLanIp
) {
    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = ProtocolConstants.DEFAULT_PORT;
    public static final int DEFAULT_READ_BUFFER_BYTES = 4096;
    public static final int DEFAULT_UDP_PORT = DiscoveryConstants.DEFAULT_UDP_PORT;

    public ServerConfig {
        Objects.requireNonNull(host, "host must not be null");
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 0 and 65535: " + port);
        }
        if (maxFrameBytes <= 0) {
            throw new IllegalArgumentException("maxFrameBytes must be positive: " + maxFrameBytes);
        }
        if (readBufferBytes <= 0) {
            throw new IllegalArgumentException("readBufferBytes must be positive: " + readBufferBytes);
        }
        if (udpPort < 0 || udpPort > 65535) {
            throw new IllegalArgumentException("udpPort must be between 0 and 65535: " + udpPort);
        }
    }

    /**
     * Backwards-compatible 4-argument constructor.
     */
    public ServerConfig(String host, int port, int maxFrameBytes, int readBufferBytes) {
        this(host, port, maxFrameBytes, readBufferBytes, true, DEFAULT_UDP_PORT, null);
    }

    public static ServerConfig defaultConfig() {
        return new ServerConfig(
            DEFAULT_HOST,
            DEFAULT_PORT,
            ProtocolConstants.MAX_FRAME_BYTES,
            DEFAULT_READ_BUFFER_BYTES,
            true,
            DEFAULT_UDP_PORT,
            null
        );
    }

    public static ServerConfig of(String host, int port) {
        return new ServerConfig(
            host,
            port,
            ProtocolConstants.MAX_FRAME_BYTES,
            DEFAULT_READ_BUFFER_BYTES,
            true,
            DEFAULT_UDP_PORT,
            null
        );
    }

    public static ServerConfig of(String host, int port, boolean udpEnabled, int udpPort) {
        return new ServerConfig(
            host,
            port,
            ProtocolConstants.MAX_FRAME_BYTES,
            DEFAULT_READ_BUFFER_BYTES,
            udpEnabled,
            udpPort,
            null
        );
    }
}
