package com.seatsync.common.protocol.request;

import com.seatsync.common.model.SeatStatus;
import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.RequestMessage;
import java.util.Objects;

/**
 * Administrative request to update a seat's status (e.g. maintenance, VIP block).
 */
public record AdminUpdateSeatStatusRequest(
    String requestId,
    String showId,
    String seatId,
    SeatStatus status
) implements RequestMessage {
    public AdminUpdateSeatStatusRequest {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(showId, "showId must not be null");
        Objects.requireNonNull(seatId, "seatId must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.ADMIN_UPDATE_SEAT_STATUS;
    }
}
