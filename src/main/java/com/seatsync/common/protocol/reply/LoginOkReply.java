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
    String username,
    com.seatsync.common.model.UserRole role
) implements ReplyMessage {
    public LoginOkReply {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(username, "username must not be null");
        role = role == null ? com.seatsync.common.model.UserRole.CUSTOMER : role;
    }

    public LoginOkReply(String requestId, String userId, String username) {
        this(requestId, userId, username, com.seatsync.common.model.UserRole.CUSTOMER);
    }

    @Override
    public MessageType getType() {
        return MessageType.LOGIN_OK;
    }
}
