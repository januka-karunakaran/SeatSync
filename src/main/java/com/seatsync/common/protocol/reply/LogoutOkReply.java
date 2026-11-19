package com.seatsync.common.protocol.reply;

import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.ReplyMessage;
import java.util.Objects;

/**
 * Successful logout reply.
 */
public record LogoutOkReply(
    String requestId,
    String message
) implements ReplyMessage {
    public LogoutOkReply {
        Objects.requireNonNull(requestId, "requestId must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.LOGOUT_OK;
    }
}
