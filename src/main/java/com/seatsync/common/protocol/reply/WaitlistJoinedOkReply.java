package com.seatsync.common.protocol.reply;

import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.ReplyMessage;
import java.util.Objects;

/**
 * Successful waitlist join reply.
 */
public record WaitlistJoinedOkReply(
    String requestId,
    String waitlistId,
    String showId,
    int position,
    String message
) implements ReplyMessage {
    public WaitlistJoinedOkReply {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(waitlistId, "waitlistId must not be null");
        Objects.requireNonNull(showId, "showId must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.WAITLIST_JOINED_OK;
    }
}
