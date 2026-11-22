package com.seatsync.common.model;

import java.util.Objects;

/**
 * UDP Discovery Beacon payload broadcast across local subnet.
 */
public record UdpBeaconPayload(
    String serviceName,
    String version,
    String tcpHost,
    int tcpPort,
    int tlsPort,
    int activeConnections,
    long timestamp
) {
    public static final String DEFAULT_SERVICE_NAME = "SeatSync";
    public static final String PROTOCOL_VERSION_2 = "2.0";
    public static final int DEFAULT_UDP_PORT = 8888;

    public UdpBeaconPayload {
        Objects.requireNonNull(serviceName, "serviceName must not be null");
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(tcpHost, "tcpHost must not be null");
    }
}
