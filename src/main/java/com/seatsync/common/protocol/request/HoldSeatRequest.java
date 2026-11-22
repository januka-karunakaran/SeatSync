package com.seatsync.common.protocol.request;

import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.RequestMessage;
import java.util.Objects;

/**
 * Request to place a temporary hold on a seat before checkout.
 * Identity is inferred strictly from session RequestContext.
 */
public record HoldSeatRequest(
    String requestId,
    String showId,
    String seatId,
    int holdDurationSeconds
) implements RequestMessage {
    public HoldSeatRequest {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(showId, "showId must not be null");
        Objects.requireNonNull(seatId, "seatId must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.HOLD_SEAT;
    }
}
