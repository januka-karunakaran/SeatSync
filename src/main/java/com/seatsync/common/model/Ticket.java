package com.seatsync.common.model;

import java.util.Objects;

/**
 * Immutable digital ticket representation with cryptographic verification hash.
 */
public record Ticket(
    String ticketId,
    String bookingId,
    String showId,
    String seatId,
    String userId,
    long issuedAt,
    String verificationHash,
    int priceInCents
) {
    public Ticket {
        Objects.requireNonNull(ticketId, "ticketId must not be null");
        Objects.requireNonNull(bookingId, "bookingId must not be null");
        Objects.requireNonNull(showId, "showId must not be null");
        Objects.requireNonNull(seatId, "seatId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(verificationHash, "verificationHash must not be null");
    }
}
