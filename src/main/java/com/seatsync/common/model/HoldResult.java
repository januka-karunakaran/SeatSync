package com.seatsync.common.model;

/**
 * Result of attempting to place a temporary hold on a seat.
 */
public record HoldResult(
    boolean success,
    String holdId,
    String showId,
    String seatId,
    long expiresAt,
    String message,
    ErrorCode errorCode
) {
    public static HoldResult ok(String holdId, String showId, String seatId, long expiresAt, String message) {
        return new HoldResult(true, holdId, showId, seatId, expiresAt, message, null);
    }

    public static HoldResult rejected(String showId, String seatId, ErrorCode errorCode, String message) {
        return new HoldResult(false, null, showId, seatId, 0L, message, errorCode);
    }
}
