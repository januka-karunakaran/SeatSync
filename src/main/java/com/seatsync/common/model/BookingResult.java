package com.seatsync.common.model;

/**
 * Result of a seat booking transaction.
 *
 * @param success True if the booking completed successfully
 * @param bookingId Unique booking reference (null if booking failed)
 * @param showId Target show ID
 * @param seatId Target seat ID
 * @param message Human-readable message or reason
 * @param errorCode Specific server error code if rejected (null on success)
 */
public record BookingResult(
    boolean success,
    String bookingId,
    String showId,
    String seatId,
    String message,
    ErrorCode errorCode
) {
    public static BookingResult ok(String bookingId, String showId, String seatId, String message) {
        return new BookingResult(true, bookingId, showId, seatId, message, null);
    }

    public static BookingResult rejected(String showId, String seatId, ErrorCode errorCode, String message) {
        return new BookingResult(false, null, showId, seatId, message, errorCode);
    }
}
