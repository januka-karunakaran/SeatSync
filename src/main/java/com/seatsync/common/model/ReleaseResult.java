package com.seatsync.common.model;

/**
 * Result of attempting to release a temporary seat hold.
 */
public record ReleaseResult(
    boolean success,
    String holdId,
    String showId,
    String seatId,
    String message,
    ErrorCode errorCode
) {
    public static ReleaseResult ok(String holdId, String showId, String seatId, String message) {
        return new ReleaseResult(true, holdId, showId, seatId, message, null);
    }

    public static ReleaseResult rejected(String holdId, ErrorCode errorCode, String message) {
        return new ReleaseResult(false, holdId, null, null, message, errorCode);
    }
}
