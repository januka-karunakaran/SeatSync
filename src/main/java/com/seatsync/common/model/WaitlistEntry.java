package com.seatsync.common.model;

import java.util.Objects;

/**
 * FIFO queue entry for a sold-out show waitlist.
 */
public record WaitlistEntry(
    String waitlistId,
    String showId,
    String userId,
    int position,
    long timestamp
) {
    public WaitlistEntry {
        Objects.requireNonNull(waitlistId, "waitlistId must not be null");
        Objects.requireNonNull(showId, "showId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
    }
}
