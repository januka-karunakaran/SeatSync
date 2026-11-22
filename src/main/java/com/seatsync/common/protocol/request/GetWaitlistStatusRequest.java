package com.seatsync.common.protocol.request;

import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.RequestMessage;
import java.util.Objects;

/**
 * Request to retrieve user's current waitlist queue position for a show.
 */
public record GetWaitlistStatusRequest(
    String requestId,
    String showId
) implements RequestMessage {
    public GetWaitlistStatusRequest {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(showId, "showId must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.GET_WAITLIST_STATUS;
    }
}
