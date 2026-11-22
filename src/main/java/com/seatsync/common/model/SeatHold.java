package com.seatsync.common.model;

import java.util.Objects;

/**
 * Represents an active temporary hold on a seat before booking confirmation.
 *
 * @param holdId               Unique hold reservation token
 * @param showId               Target show identifier
 * @param seatId               Target seat identifier
 * @param heldByUserId         Authenticated user holding the seat
 * @param holdTimestamp        Epoch millisecond when hold was acquired
 * @param expirationTimestamp  Epoch millisecond when hold expires if unconfirmed
 * @param confirmed            True if converted to a completed booking
 */
public record SeatHold(
    String holdId,
    String showId,
    String seatId,
    String heldByUserId,
    long holdTimestamp,
    long expirationTimestamp,
    boolean confirmed
) {
    public SeatHold {
        Objects.requireNonNull(holdId, "holdId must not be null");
        Objects.requireNonNull(showId, "showId must not be null");
        Objects.requireNonNull(seatId, "seatId must not be null");
        Objects.requireNonNull(heldByUserId, "heldByUserId must not be null");
    }

    public boolean isExpired(long currentTimeMillis) {
        return !confirmed && currentTimeMillis >= expirationTimestamp;
    }
}
