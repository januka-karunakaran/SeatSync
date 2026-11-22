package com.seatsync.common.protocol.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.RequestMessage;

import java.util.Objects;

/**
 * Request to retrieve detailed NIO traffic and network metrics.
 * Privileged operation restricted to ADMIN roles.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GetNetworkMetricsRequest(
    @JsonProperty("requestId") String requestId
) implements RequestMessage {
    public GetNetworkMetricsRequest {
        Objects.requireNonNull(requestId, "requestId must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.GET_NETWORK_METRICS;
    }
}
