package com.seatsync.common.protocol.request;

import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.RequestMessage;
import java.util.Objects;

/**
 * Request to subscribe client session to real-time events for a specific show.
 */
public record SubscribeEventsRequest(
    String requestId,
    String showId
) implements RequestMessage {
    public SubscribeEventsRequest {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(showId, "showId must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.SUBSCRIBE_EVENTS;
    }
}
