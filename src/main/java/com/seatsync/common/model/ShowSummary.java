package com.seatsync.common.model;

import java.util.Objects;

/**
 * High-level summary of a scheduled show in the multi-show catalog.
 *
 * @param showId         Unique show identifier (e.g. SHOW-01, SHOW-02)
 * @param title          Performance title
 * @param startTime      ISO-8601 formatted start timestamp or human-readable schedule
 * @param venue          Venue / auditorium name
 * @param totalSeats     Total capacity
 * @param availableSeats Number of currently unbooked and unheld seats
 */
public record ShowSummary(
    String showId,
    String title,
    String startTime,
    String venue,
    int totalSeats,
    int availableSeats
) {
    public ShowSummary {
        Objects.requireNonNull(showId, "showId must not be null");
        Objects.requireNonNull(title, "title must not be null");
    }
}
