package com.seatsync.network;

import com.seatsync.common.model.ProtocolConstants;
import com.seatsync.common.model.TlsConfigMode;
import com.seatsync.network.discovery.DiscoveryConstants;

import javax.net.ssl.SSLContext;
import java.util.Objects;

/**
 * Immutable configuration for the non-blocking Java NIO booking server, UDP discovery, and TLS transport.
 *
 * @param host                Listening host address (default: 127.0.0.1)
 * @param port                Listening plaintext TCP port (default: 8080, 0 for dynamic ephemeral allocation)
 * @param maxFrameBytes       Maximum allowable line frame size in bytes (default: 16 KiB)
 * @param readBufferBytes     Direct/Heap buffer size allocated per read operation
 * @param tlsMode             TLS operation mode (DISABLED, OPTIONAL, REQUIRED)
 * @param tlsPort             Listening secure TLS port (default: 8443, 0 for dynamic ephemeral allocation)
 * @param sslContext          Server SSLContext (must be non-null if tlsMode != DISABLED)
 * @param udpDiscoveryEnabled Whether the UDP discovery listener should start with the server
 * @param udpPort             Listening UDP port for discovery (default: 5051)
 * @param manualLanIp         Optional manual LAN IP to advertise instead of auto-detection
 */
public record ServerConfig(
    String host,
    int port,
    int maxFrameBytes,
    int readBufferBytes,
    TlsConfigMode tlsMode,
    int tlsPort,
    SSLContext sslContext,
    boolean udpDiscoveryEnabled,
    int udpPort,
    String manualLanIp
) {
    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = ProtocolConstants.DEFAULT_PORT;
    public static final int DEFAULT_READ_BUFFER_BYTES = 4096;
    public static final int DEFAULT_TLS_PORT = 8443;
    public static final int DEFAULT_UDP_PORT = DiscoveryConstants.DEFAULT_UDP_PORT;

    public ServerConfig {
        Objects.requireNonNull(host, "host must not be null");
        Objects.requireNonNull(tlsMode, "tlsMode must not be null");
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 0 and 65535: " + port);
        }
        if (maxFrameBytes <= 0) {
            throw new IllegalArgumentException("maxFrameBytes must be positive: " + maxFrameBytes);
        }
        if (readBufferBytes <= 0) {
            throw new IllegalArgumentException("readBufferBytes must be positive: " + readBufferBytes);
        }
        if (tlsPort < 0 || tlsPort > 65535) {
            throw new IllegalArgumentException("tlsPort must be between 0 and 65535: " + tlsPort);
        }
        if (udpPort < 0 || udpPort > 65535) {
            throw new IllegalArgumentException("udpPort must be between 0 and 65535: " + udpPort);
        }
    }

    /**
     * Backwards-compatible 4-argument constructor.
     */
    public ServerConfig(String host, int port, int maxFrameBytes, int readBufferBytes) {
        this(host, port, maxFrameBytes, readBufferBytes, TlsConfigMode.DISABLED, DEFAULT_TLS_PORT, null, true, DEFAULT_UDP_PORT, null);
    }

    /**
     * Backwards-compatible TLS constructor.
     */
    public ServerConfig(String host, int port, int maxFrameBytes, int readBufferBytes, TlsConfigMode tlsMode, int tlsPort, SSLContext sslContext) {
        this(host, port, maxFrameBytes, readBufferBytes, tlsMode, tlsPort, sslContext, true, DEFAULT_UDP_PORT, null);
    }

    /**
     * Backwards-compatible UDP discovery constructor.
     */
    public ServerConfig(String host, int port, int maxFrameBytes, int readBufferBytes, boolean udpDiscoveryEnabled, int udpPort, String manualLanIp) {
        this(host, port, maxFrameBytes, readBufferBytes, TlsConfigMode.DISABLED, DEFAULT_TLS_PORT, null, udpDiscoveryEnabled, udpPort, manualLanIp);
    }

    public static ServerConfig defaultConfig() {
        return new ServerConfig(
            DEFAULT_HOST,
            DEFAULT_PORT,
            ProtocolConstants.MAX_FRAME_BYTES,
            DEFAULT_READ_BUFFER_BYTES,
            TlsConfigMode.DISABLED,
            DEFAULT_TLS_PORT,
            null,
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
            TlsConfigMode.DISABLED,
            DEFAULT_TLS_PORT,
            null,
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
            TlsConfigMode.DISABLED,
            DEFAULT_TLS_PORT,
            null,
            udpEnabled,
            udpPort,
            null
        );
    }

    public static ServerConfig withTls(String host, int plainPort, int tlsPort, SSLContext sslContext) {
        return new ServerConfig(
            host,
            plainPort,
            ProtocolConstants.MAX_FRAME_BYTES,
            DEFAULT_READ_BUFFER_BYTES,
            TlsConfigMode.REQUIRED,
            tlsPort,
            sslContext,
            true,
            DEFAULT_UDP_PORT,
            null
        );
    }

    public static ServerConfig withDualTransport(String host, int plainPort, int tlsPort, SSLContext sslContext) {
        return new ServerConfig(
            host,
            plainPort,
            ProtocolConstants.MAX_FRAME_BYTES,
            DEFAULT_READ_BUFFER_BYTES,
            TlsConfigMode.OPTIONAL,
            tlsPort,
            sslContext,
            true,
            DEFAULT_UDP_PORT,
            null
        );
    }
}
