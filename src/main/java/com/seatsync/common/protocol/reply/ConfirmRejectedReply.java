package com.seatsync.common.protocol.reply;

import com.seatsync.common.model.ErrorCode;
import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.ReplyMessage;
import java.util.Objects;

/**
 * Rejected hold confirmation reply.
 */
public record ConfirmRejectedReply(
    String requestId,
    String holdId,
    ErrorCode errorCode,
    String message
) implements ReplyMessage {
    public ConfirmRejectedReply {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.CONFIRM_REJECTED;
    }
}
