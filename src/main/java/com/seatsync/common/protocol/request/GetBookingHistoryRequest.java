package com.seatsync.common.protocol.request;

import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.RequestMessage;
import java.util.Objects;

/**
 * Request to retrieve paginated booking history for the authenticated user.
 */
public record GetBookingHistoryRequest(
    String requestId,
    int pageNumber,
    int pageSize
) implements RequestMessage {
    public GetBookingHistoryRequest {
        Objects.requireNonNull(requestId, "requestId must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.GET_BOOKING_HISTORY;
    }
}
