package com.seatsync.common.protocol.reply;

import com.seatsync.common.model.ErrorCode;
import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.ReplyMessage;
import java.util.Objects;

/**
 * Failed authentication reply.
 */
public record LoginFailedReply(
    String requestId,
    ErrorCode errorCode,
    String message
) implements ReplyMessage {
    public LoginFailedReply {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.LOGIN_FAILED;
    }
}
