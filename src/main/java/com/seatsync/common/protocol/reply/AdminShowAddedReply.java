package com.seatsync.common.protocol.reply;

import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.ReplyMessage;
import java.util.Objects;

/**
 * Administrative reply confirming show was added to the catalog.
 */
public record AdminShowAddedReply(
    String requestId,
    String showId,
    String message
) implements ReplyMessage {
    public AdminShowAddedReply {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(showId, "showId must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.ADMIN_SHOW_ADDED;
    }
}
