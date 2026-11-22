package com.seatsync.common.protocol.request;

import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.RequestMessage;
import java.util.Objects;

/**
 * Request to unsubscribe client session from real-time events for a show.
 */
public record UnsubscribeEventsRequest(
    String requestId,
    String showId
) implements RequestMessage {
    public UnsubscribeEventsRequest {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(showId, "showId must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.UNSUBSCRIBE_EVENTS;
    }
}
