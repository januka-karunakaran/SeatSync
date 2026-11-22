package com.seatsync.network.discovery;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.UUID;

/**
 * Bounded UDP discovery probe request datagram payload.
 *
 * @param marker Protocol identification marker (must match {@link DiscoveryConstants#PROTOCOL_MARKER_REQUEST})
 * @param nonce  Random client-generated token to correlate response and reject replays
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DiscoveryRequest(
    @JsonProperty("marker") String marker,
    @JsonProperty("nonce") String nonce
) {
    public DiscoveryRequest {
        Objects.requireNonNull(marker, "marker must not be null");
        Objects.requireNonNull(nonce, "nonce must not be null");
        if (nonce.length() > DiscoveryConstants.MAX_NONCE_LENGTH) {
            throw new IllegalArgumentException("Nonce exceeds max length of " + DiscoveryConstants.MAX_NONCE_LENGTH);
        }
    }

    public static DiscoveryRequest create() {
        return new DiscoveryRequest(
            DiscoveryConstants.PROTOCOL_MARKER_REQUEST,
            UUID.randomUUID().toString()
        );
    }

    public boolean isValid() {
        return DiscoveryConstants.PROTOCOL_MARKER_REQUEST.equals(marker)
            && !nonce.isBlank()
            && nonce.length() <= DiscoveryConstants.MAX_NONCE_LENGTH;
    }
}
