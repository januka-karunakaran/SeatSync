package com.seatsync.common.protocol.reply;

import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.ReplyMessage;
import java.util.Objects;

/**
 * User waitlist queue status reply.
 */
public record WaitlistStatusReply(
    String requestId,
    String showId,
    int position,
    int totalWaitlistSize
) implements ReplyMessage {
    public WaitlistStatusReply {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(showId, "showId must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.WAITLIST_STATUS_REPLY;
    }
}
