package com.seatsync.common.model;

import java.util.List;

/**
 * Result of an all-or-nothing hold covering one or more seats in the same show.
 */
public record MultiHoldResult(
    boolean success,
    String showId,
    List<String> seatIds,
    List<String> holdIds,
    long expiresAt,
    String message,
    ErrorCode errorCode
) {
    public MultiHoldResult {
        seatIds = seatIds == null ? List.of() : List.copyOf(seatIds);
        holdIds = holdIds == null ? List.of() : List.copyOf(holdIds);
    }

    public static MultiHoldResult ok(String showId, List<String> seatIds, List<String> holdIds, long expiresAt, String message) {
        return new MultiHoldResult(true, showId, seatIds, holdIds, expiresAt, message, null);
    }

    public static MultiHoldResult rejected(String showId, List<String> seatIds, ErrorCode errorCode, String message) {
        return new MultiHoldResult(false, showId, seatIds, List.of(), 0L, message, errorCode);
    }
}
