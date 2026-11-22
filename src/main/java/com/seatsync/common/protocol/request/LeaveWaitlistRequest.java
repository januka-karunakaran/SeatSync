package com.seatsync.common.protocol.request;

import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.RequestMessage;
import java.util.Objects;

/**
 * Request to leave the waitlist for a show.
 */
public record LeaveWaitlistRequest(
    String requestId,
    String showId
) implements RequestMessage {
    public LeaveWaitlistRequest {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(showId, "showId must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.LEAVE_WAITLIST;
    }
}
