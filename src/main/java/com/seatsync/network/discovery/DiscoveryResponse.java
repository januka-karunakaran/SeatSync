package com.seatsync.network.discovery;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Bounded UDP discovery reply datagram payload.
 * Sent back to the probing client to advertise reachable connectivity details.
 *
 * @param marker      Protocol response marker (must match {@link DiscoveryConstants#PROTOCOL_MARKER_RESPONSE})
 * @param nonce       Correlation nonce matching probe request
 * @param serverIp    Reachable LAN IP (never 0.0.0.0)
 * @param tcpPort     Configured server listening TCP port
 * @param tlsRequired Flag indicating whether TLS handshake is mandatory
 * @param tlsPort     Configured TLS port (or 0 if disabled)
 * @param fingerprint Optional fingerprint hint (e.g. certificate hash or server build ID)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DiscoveryResponse(
    @JsonProperty("marker") String marker,
    @JsonProperty("nonce") String nonce,
    @JsonProperty("serverIp") String serverIp,
    @JsonProperty("tcpPort") int tcpPort,
    @JsonProperty("tlsRequired") boolean tlsRequired,
    @JsonProperty("tlsPort") int tlsPort,
    @JsonProperty("fingerprint") String fingerprint
) {
    public DiscoveryResponse {
        Objects.requireNonNull(marker, "marker must not be null");
        Objects.requireNonNull(nonce, "nonce must not be null");
        Objects.requireNonNull(serverIp, "serverIp must not be null");
        if ("0.0.0.0".equals(serverIp)) {
            throw new IllegalArgumentException("serverIp must not be 0.0.0.0; advertise a reachable LAN IP");
        }
        if (tcpPort <= 0 || tcpPort > 65535) {
            throw new IllegalArgumentException("tcpPort out of valid range: " + tcpPort);
        }
    }

    public boolean isValidFor(String expectedNonce) {
        return DiscoveryConstants.PROTOCOL_MARKER_RESPONSE.equals(marker)
            && Objects.equals(this.nonce, expectedNonce)
            && !"0.0.0.0".equals(serverIp);
    }
}
