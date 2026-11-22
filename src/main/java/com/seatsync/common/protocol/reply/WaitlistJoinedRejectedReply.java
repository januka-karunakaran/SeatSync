package com.seatsync.common.protocol.reply;

import com.seatsync.common.model.ErrorCode;
import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.ReplyMessage;
import java.util.Objects;

/**
 * Rejected waitlist join reply.
 */
public record WaitlistJoinedRejectedReply(
    String requestId,
    String showId,
    ErrorCode errorCode,
    String message
) implements ReplyMessage {
    public WaitlistJoinedRejectedReply {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.WAITLIST_JOINED_REJECTED;
    }
}
