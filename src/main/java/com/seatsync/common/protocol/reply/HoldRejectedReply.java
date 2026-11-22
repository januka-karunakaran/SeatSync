package com.seatsync.common.protocol.reply;

import com.seatsync.common.model.ErrorCode;
import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.ReplyMessage;
import java.util.Objects;

/**
 * Rejected seat hold reply.
 */
public record HoldRejectedReply(
    String requestId,
    String showId,
    String seatId,
    ErrorCode errorCode,
    String message
) implements ReplyMessage {
    public HoldRejectedReply {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.HOLD_REJECTED;
    }
}
