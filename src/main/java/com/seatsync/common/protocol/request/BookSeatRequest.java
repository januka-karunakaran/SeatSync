package com.seatsync.common.protocol.request;

import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.RequestMessage;
import java.util.Objects;

/**
 * Request to reserve a seat in a show.
 * <p>
 * Note: To prevent identity spoofing, the requesting user's identity is NEVER accepted
 * from client payload; it is strictly derived from the authenticated session context.
 *
 * @param requestId Unique client request ID
 * @param showId    Target show identifier
 * @param seatId    Target seat identifier (A1-E8)
 */
public record BookSeatRequest(
    String requestId,
    String showId,
    String seatId
) implements RequestMessage {
    public BookSeatRequest {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(showId, "showId must not be null");
        Objects.requireNonNull(seatId, "seatId must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.BOOK_SEAT;
    }
}
