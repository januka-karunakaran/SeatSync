package com.seatsync.common.model;

/**
 * Result of waitlist operations (join/leave/status).
 */
public record WaitlistResult(
    boolean success,
    String waitlistId,
    String showId,
    int position,
    String message,
    ErrorCode errorCode
) {
    public static WaitlistResult ok(String waitlistId, String showId, int position, String message) {
        return new WaitlistResult(true, waitlistId, showId, position, message, null);
    }

    public static WaitlistResult rejected(String showId, ErrorCode errorCode, String message) {
        return new WaitlistResult(false, null, showId, -1, message, errorCode);
    }
}
