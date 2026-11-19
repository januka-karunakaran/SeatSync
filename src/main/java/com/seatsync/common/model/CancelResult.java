package com.seatsync.common.model;

/**
 * Result of a booking cancellation transaction.
 *
 * @param success True if the booking was cancelled successfully
 * @param bookingId Booking identifier cancelled or attempted
 * @param seatId Identifier of the released seat (null if failed)
 * @param message Human-readable message or reason
 * @param errorCode Specific server error code if rejected (null on success)
 */
public record CancelResult(
    boolean success,
    String bookingId,
    String seatId,
    String message,
    ErrorCode errorCode
) {
    public static CancelResult ok(String bookingId, String seatId, String message) {
        return new CancelResult(true, bookingId, seatId, message, null);
    }

    public static CancelResult rejected(String bookingId, ErrorCode errorCode, String message) {
        return new CancelResult(false, bookingId, null, message, errorCode);
    }
}
