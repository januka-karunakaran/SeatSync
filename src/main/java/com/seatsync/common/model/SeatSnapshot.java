package com.seatsync.common.model;

import java.util.List;
import java.util.Objects;

/**
 * Immutable point-in-time snapshot of all seat allocations for a given show.
 *
 * @param showId Show identifier (e.g. SHOW-01)
 * @param version Monotonically increasing revision number of the seat state
 * @param seats Immutable list of seat views
 */
public record SeatSnapshot(
    String showId,
    long version,
    List<SeatView> seats
) {
    public SeatSnapshot {
        Objects.requireNonNull(showId, "showId must not be null");
        seats = (seats == null) ? List.of() : List.copyOf(seats);
    }
}
