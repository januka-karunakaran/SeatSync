package com.seatsync.common.protocol.reply;

import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.ReplyMessage;
import java.util.Objects;

/**
 * Successful seat hold release reply.
 */
public record HoldReleasedOkReply(
    String requestId,
    String holdId,
    String showId,
    String seatId,
    String message
) implements ReplyMessage {
    public HoldReleasedOkReply {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(holdId, "holdId must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.HOLD_RELEASED_OK;
    }
}
