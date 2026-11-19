package com.seatsync.common.protocol.reply;

import com.seatsync.common.model.ErrorCode;
import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.ReplyMessage;
import java.util.Objects;

/**
 * Generic or protocol-level error reply.
 */
public record ErrorReply(
    String requestId,
    ErrorCode errorCode,
    String message
) implements ReplyMessage {
    public ErrorReply {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.ERROR;
    }
}
