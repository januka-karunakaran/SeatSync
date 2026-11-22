package com.seatsync.common.protocol.reply;

import com.seatsync.common.model.ErrorCode;
import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.ReplyMessage;
import java.util.Objects;

/**
 * Rejected seat hold release reply.
 */
public record HoldReleasedRejectedReply(
    String requestId,
    String holdId,
    ErrorCode errorCode,
    String message
) implements ReplyMessage {
    public HoldReleasedRejectedReply {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.HOLD_RELEASED_REJECTED;
    }
}
