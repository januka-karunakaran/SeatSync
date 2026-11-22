package com.seatsync.common.protocol.reply;

import com.seatsync.common.model.SeatStatus;
import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.ReplyMessage;
import java.util.Objects;

/**
 * Administrative reply confirming seat status update.
 */
public record AdminSeatUpdatedReply(
    String requestId,
    String showId,
    String seatId,
    SeatStatus status,
    String message
) implements ReplyMessage {
    public AdminSeatUpdatedReply {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(showId, "showId must not be null");
        Objects.requireNonNull(seatId, "seatId must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.ADMIN_SEAT_UPDATED;
    }
}
