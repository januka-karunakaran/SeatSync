package com.seatsync.common.model;

import java.util.List;
import java.util.Objects;

/**
 * Defines the physical grid and tiering layout for an auditorium.
 *
 * @param layoutId      Unique layout identifier
 * @param name          Auditorium name (e.g. Grand Hall, Main Stage)
 * @param rowCount      Total rows
 * @param columnCount   Total columns per row
 * @param totalCapacity Maximum seating capacity
 * @param tiers         Seating tiers mapped in this venue
 */
public record AuditoriumLayout(
    String layoutId,
    String name,
    int rowCount,
    int columnCount,
    int totalCapacity,
    List<SeatTier> tiers
) {
    public AuditoriumLayout {
        Objects.requireNonNull(layoutId, "layoutId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        tiers = (tiers == null) ? List.of() : List.copyOf(tiers);
    }
}
