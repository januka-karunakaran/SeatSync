package com.seatsync.common.protocol.reply;

import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.ReplyMessage;
import java.util.Objects;

/**
 * Reply confirming real-time event pub/sub unsubscription.
 */
public record UnsubscribeOkReply(
    String requestId,
    String showId,
    String message
) implements ReplyMessage {
    public UnsubscribeOkReply {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(showId, "showId must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.UNSUBSCRIBE_OK;
    }
}
