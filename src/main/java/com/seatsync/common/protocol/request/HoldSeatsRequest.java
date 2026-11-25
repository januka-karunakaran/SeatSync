package com.seatsync.common.protocol.request;

import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.RequestMessage;
import java.util.List;
import java.util.Objects;

/**
 * Holds every listed seat for one show, or none of them.
 * The signed-in user comes from the server session.
 */
public record HoldSeatsRequest(
    String requestId,
    String showId,
    List<String> seatIds,
    int holdDurationSeconds
) implements RequestMessage {
    public HoldSeatsRequest {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(showId, "showId must not be null");
        seatIds = seatIds == null ? List.of() : List.copyOf(seatIds);
    }

    @Override
    public MessageType getType() {
        return MessageType.HOLD_SEATS;
    }
}
