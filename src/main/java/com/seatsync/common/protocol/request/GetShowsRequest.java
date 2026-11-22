package com.seatsync.common.protocol.request;

import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.RequestMessage;
import java.util.Objects;

/**
 * Request to list all available shows in the catalog.
 */
public record GetShowsRequest(
    String requestId
) implements RequestMessage {
    public GetShowsRequest {
        Objects.requireNonNull(requestId, "requestId must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.GET_SHOWS;
    }
}
