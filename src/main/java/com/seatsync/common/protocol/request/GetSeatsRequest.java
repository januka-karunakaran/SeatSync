package com.seatsync.common.protocol.request;

import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.RequestMessage;
import java.util.Objects;

/**
 * Request to retrieve the current snapshot of seats for a given show.
 *
 * @param requestId Unique client request ID
 * @param showId    Target show ID (e.g. SHOW-01)
 */
public record GetSeatsRequest(
    String requestId,
    String showId
) implements RequestMessage {
    public GetSeatsRequest {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(showId, "showId must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.GET_SEATS;
    }
}
