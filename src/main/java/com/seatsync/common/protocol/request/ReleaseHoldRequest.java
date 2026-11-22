package com.seatsync.common.protocol.request;

import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.RequestMessage;
import java.util.Objects;

/**
 * Request to manually release an active seat hold before TTL expires.
 */
public record ReleaseHoldRequest(
    String requestId,
    String holdId
) implements RequestMessage {
    public ReleaseHoldRequest {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(holdId, "holdId must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.RELEASE_HOLD;
    }
}
