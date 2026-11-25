package com.seatsync.common.protocol.reply;

import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.ReplyMessage;
import java.util.List;
import java.util.Objects;

/**
 * Every requested seat was held together until the same expiry.
 */
public record HoldSeatsOkReply(
    String requestId,
    String showId,
    List<String> seatIds,
    List<String> holdIds,
    long expiresAt,
    String message
) implements ReplyMessage {
    public HoldSeatsOkReply {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(showId, "showId must not be null");
        seatIds = seatIds == null ? List.of() : List.copyOf(seatIds);
        holdIds = holdIds == null ? List.of() : List.copyOf(holdIds);
    }

    @Override
    public MessageType getType() {
        return MessageType.HOLD_SEATS_OK;
    }
}
