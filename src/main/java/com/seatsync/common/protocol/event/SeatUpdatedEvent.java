package com.seatsync.common.protocol.event;

import com.seatsync.common.model.SeatView;
import com.seatsync.common.protocol.Message;
import com.seatsync.common.protocol.MessageType;
import java.util.List;
import java.util.Objects;

/**
 * Asynchronous server-push broadcast event emitted whenever seat availability changes.
 * Does not carry a client requestId.
 *
 * @param showId  Target show ID
 * @param version New state revision number
 * @param seats   Updated or complete seat views
 */
public record SeatUpdatedEvent(
    String showId,
    long version,
    List<SeatView> seats
) implements Message {
    public SeatUpdatedEvent {
        Objects.requireNonNull(showId, "showId must not be null");
        seats = (seats == null) ? List.of() : List.copyOf(seats);
    }

    @Override
    public MessageType getType() {
        return MessageType.SEAT_UPDATED;
    }
}
