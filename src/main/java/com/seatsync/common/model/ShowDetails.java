package com.seatsync.common.model;

import java.util.Objects;

/**
 * Detailed representation of a show including description and auditorium layout.
 */
public record ShowDetails(
    String showId,
    String title,
    String description,
    String genre,
    String startTime,
    String endTime,
    String venue,
    AuditoriumLayout layout
) {
    public ShowDetails {
        Objects.requireNonNull(showId, "showId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(layout, "layout must not be null");
    }
}
