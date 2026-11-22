package com.seatsync.common.protocol.request;

import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.RequestMessage;
import java.util.Objects;

/**
 * Request detailed show metadata and auditorium layout.
 */
public record GetShowDetailsRequest(
    String requestId,
    String showId
) implements RequestMessage {
    public GetShowDetailsRequest {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(showId, "showId must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.GET_SHOW_DETAILS;
    }
}
