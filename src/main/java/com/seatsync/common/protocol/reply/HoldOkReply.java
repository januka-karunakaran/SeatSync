package com.seatsync.common.protocol.reply;

import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.ReplyMessage;
import java.util.Objects;

/**
 * Successful seat hold reservation reply.
 */
public record HoldOkReply(
    String requestId,
    String holdId,
    String showId,
    String seatId,
    long expiresAt,
    String message
) implements ReplyMessage {
    public HoldOkReply {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(holdId, "holdId must not be null");
        Objects.requireNonNull(showId, "showId must not be null");
        Objects.requireNonNull(seatId, "seatId must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.HOLD_OK;
    }
}
