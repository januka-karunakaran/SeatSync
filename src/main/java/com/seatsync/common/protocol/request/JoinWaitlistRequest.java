package com.seatsync.common.protocol.request;

import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.RequestMessage;
import java.util.Objects;

/**
 * Request to join the FIFO waitlist for a sold-out show.
 */
public record JoinWaitlistRequest(
    String requestId,
    String showId
) implements RequestMessage {
    public JoinWaitlistRequest {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(showId, "showId must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.JOIN_WAITLIST;
    }
}
