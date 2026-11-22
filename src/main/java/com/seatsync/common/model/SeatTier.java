package com.seatsync.common.model;

import java.util.Objects;

/**
 * Defines pricing and attributes for a seating tier.
 *
 * @param tierName     Descriptive name (e.g. VIP Front, Balcony)
 * @param tierType     Standardized tier classification
 * @param priceInCents Ticket price in integer cents (no floating-point rounding errors)
 * @param description  Optional marketing or accessibility description
 */
public record SeatTier(
    String tierName,
    TierType tierType,
    int priceInCents,
    String description
) {
    public SeatTier {
        Objects.requireNonNull(tierName, "tierName must not be null");
        Objects.requireNonNull(tierType, "tierType must not be null");
        if (priceInCents < 0) {
            throw new IllegalArgumentException("priceInCents cannot be negative: " + priceInCents);
        }
    }
}
