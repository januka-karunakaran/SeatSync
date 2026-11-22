package com.seatsync.common.protocol.reply;

import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.ReplyMessage;
import java.util.Objects;

/**
 * Successful hold confirmation and booking completion reply.
 */
public record ConfirmOkReply(
    String requestId,
    String bookingId,
    String ticketId,
    String showId,
    String seatId,
    String message
) implements ReplyMessage {
    public ConfirmOkReply {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(bookingId, "bookingId must not be null");
        Objects.requireNonNull(ticketId, "ticketId must not be null");
        Objects.requireNonNull(showId, "showId must not be null");
        Objects.requireNonNull(seatId, "seatId must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.CONFIRM_OK;
    }
}
