package com.seatsync.common.protocol.reply;

import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.ReplyMessage;
import java.util.Objects;

/**
 * Reply confirming real-time event pub/sub subscription.
 */
public record SubscribeOkReply(
    String requestId,
    String showId,
    String message
) implements ReplyMessage {
    public SubscribeOkReply {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(showId, "showId must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.SUBSCRIBE_OK;
    }
}
