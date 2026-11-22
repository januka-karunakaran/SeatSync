package com.seatsync.common.protocol.event;

import com.seatsync.common.protocol.Message;
import com.seatsync.common.protocol.MessageType;
import java.util.Objects;

/**
 * Server-push event broadcast when a temporary seat hold is released or expires back to AVAILABLE.
 */
public record SeatReleasedEvent(
    String showId,
    String seatId,
    String holdId,
    long version
) implements Message {
    public SeatReleasedEvent {
        Objects.requireNonNull(showId, "showId must not be null");
        Objects.requireNonNull(seatId, "seatId must not be null");
        Objects.requireNonNull(holdId, "holdId must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.SEAT_RELEASED_EVENT;
    }
}
