package com.seatsync.common.protocol.reply;

import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.ReplyMessage;
import java.util.Objects;

/**
 * Successful waitlist departure reply.
 */
public record WaitlistLeftOkReply(
    String requestId,
    String showId,
    String message
) implements ReplyMessage {
    public WaitlistLeftOkReply {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(showId, "showId must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.WAITLIST_LEFT_OK;
    }
}
