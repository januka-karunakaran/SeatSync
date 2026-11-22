package com.seatsync.common.protocol.reply;

import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.ReplyMessage;
import java.util.Objects;

/**
 * Administrative reply confirming emergency cancellation of a seat hold.
 */
public record AdminHoldCancelledReply(
    String requestId,
    String holdId,
    String message
) implements ReplyMessage {
    public AdminHoldCancelledReply {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(holdId, "holdId must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.ADMIN_HOLD_CANCELLED;
    }
}
