package com.seatsync.common.protocol.reply;

import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.ReplyMessage;
import java.util.Objects;

/**
 * Successful authentication reply.
 */
public record LoginOkReply(
    String requestId,
    String userId,
    String username
) implements ReplyMessage {
    public LoginOkReply {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(username, "username must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.LOGIN_OK;
    }
}
