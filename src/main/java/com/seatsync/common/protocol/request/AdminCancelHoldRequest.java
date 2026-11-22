package com.seatsync.common.protocol.request;

import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.RequestMessage;
import java.util.Objects;

/**
 * Administrative request to forcibly cancel an active seat hold.
 */
public record AdminCancelHoldRequest(
    String requestId,
    String holdId,
    String reason
) implements RequestMessage {
    public AdminCancelHoldRequest {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(holdId, "holdId must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.ADMIN_CANCEL_HOLD;
    }
}
