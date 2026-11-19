package com.seatsync.common.protocol.reply;

import com.seatsync.common.model.SeatView;
import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.ReplyMessage;
import java.util.List;
import java.util.Objects;

/**
 * Seat snapshot reply containing complete current state of a show.
 */
public record SeatSnapshotReply(
    String requestId,
    String showId,
    long version,
    List<SeatView> seats
) implements ReplyMessage {
    public SeatSnapshotReply {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(showId, "showId must not be null");
        seats = (seats == null) ? List.of() : List.copyOf(seats);
    }

    @Override
    public MessageType getType() {
        return MessageType.SEAT_SNAPSHOT;
    }
}
