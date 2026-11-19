package com.seatsync.common.protocol.reply;

import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.ReplyMessage;
import java.util.Objects;

/**
 * Successful booking cancellation reply.
 */
public record CancelOkReply(
    String requestId,
    String bookingId,
    String seatId,
    String message
) implements ReplyMessage {
    public CancelOkReply {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(bookingId, "bookingId must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.CANCEL_OK;
    }
}
