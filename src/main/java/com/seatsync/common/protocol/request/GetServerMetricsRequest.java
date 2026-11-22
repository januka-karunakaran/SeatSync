package com.seatsync.common.protocol.request;

import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.RequestMessage;
import java.util.Objects;

/**
 * Request to retrieve server telemetry metrics.
 */
public record GetServerMetricsRequest(
    String requestId
) implements RequestMessage {
    public GetServerMetricsRequest {
        Objects.requireNonNull(requestId, "requestId must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.GET_SERVER_METRICS;
    }
}
