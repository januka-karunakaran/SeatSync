package com.seatsync.common.model;

import java.util.Objects;

/**
 * Immutable view of a single seat's state in a show.
 *
 * @param seatId Identifier of the seat (e.g. A1 through E8)
 * @param status Status of the seat (AVAILABLE or BOOKED)
 * @param bookedBy Masked or direct identifier of the user who booked this seat (null if available)
 */
public record SeatView(
    String seatId,
    SeatStatus status,
    String bookedBy
) {
    public SeatView {
        Objects.requireNonNull(seatId, "seatId must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }

    public static SeatView available(String seatId) {
        return new SeatView(seatId, SeatStatus.AVAILABLE, null);
    }

    public static SeatView booked(String seatId, String bookedBy) {
        return new SeatView(seatId, SeatStatus.BOOKED, bookedBy);
    }
}
