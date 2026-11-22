package com.seatsync.common.protocol.event;

import com.seatsync.common.protocol.Message;
import com.seatsync.common.protocol.MessageType;
import java.util.Objects;

/**
 * Server-push event broadcast when a seat is temporarily held by a user.
 */
public record SeatHeldEvent(
    String showId,
    String seatId,
    String holdId,
    long expiresAt,
    long version
) implements Message {
    public SeatHeldEvent {
        Objects.requireNonNull(showId, "showId must not be null");
        Objects.requireNonNull(seatId, "seatId must not be null");
        Objects.requireNonNull(holdId, "holdId must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.SEAT_HELD_EVENT;
    }
}
