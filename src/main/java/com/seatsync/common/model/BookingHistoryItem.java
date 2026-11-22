package com.seatsync.common.model;

import java.util.Objects;

/**
 * Single booking history item for user booking archives.
 */
public record BookingHistoryItem(
    String bookingId,
    String showId,
    String seatId,
    String showTitle,
    String showTime,
    String status,
    long timestamp,
    String ticketId
) {
    public BookingHistoryItem {
        Objects.requireNonNull(bookingId, "bookingId must not be null");
        Objects.requireNonNull(showId, "showId must not be null");
        Objects.requireNonNull(seatId, "seatId must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}
