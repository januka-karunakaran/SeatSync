package com.seatsync.common.protocol.request;

import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.RequestMessage;
import java.util.Objects;

/**
 * Request to cancel an existing booking.
 * <p>
 * Note: To prevent identity spoofing, the requesting user's identity is NEVER accepted
 * from client payload; it is strictly derived from the authenticated session context.
 *
 * @param requestId Unique client request ID
 * @param bookingId Booking identifier to cancel
 */
public record CancelBookingRequest(
    String requestId,
    String bookingId
) implements RequestMessage {
    public CancelBookingRequest {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(bookingId, "bookingId must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.CANCEL_BOOKING;
    }
}
