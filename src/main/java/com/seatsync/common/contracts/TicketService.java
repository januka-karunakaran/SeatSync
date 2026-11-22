package com.seatsync.common.contracts;

import com.seatsync.common.model.Ticket;

import java.util.Optional;

/**
 * Service contract for digital ticket issuance, retrieval, and cryptographic verification.
 */
public interface TicketService {

    /**
     * Issues a digital ticket upon successful booking confirmation.
     *
     * @param bookingId    Booking reference
     * @param userId       Ticket owner
     * @param showId       Target show ID
     * @param seatId       Target seat ID
     * @param priceInCents Ticket price in integer cents
     * @return Generated Ticket with SHA-256 verification hash
     */
    Ticket issueTicket(String bookingId, String userId, String showId, String seatId, int priceInCents);

    /**
     * Retrieves an issued ticket for an authenticated user.
     *
     * @param ticketId Unique ticket ID
     * @param userId   Calling user ID (must be ticket owner or admin)
     * @return Optional containing Ticket if found
     */
    Optional<Ticket> getTicket(String ticketId, String userId);

    /**
     * Verifies the authenticity of a ticket against its cryptographic hash.
     *
     * @param ticketId         Ticket identifier
     * @param verificationHash Hex-encoded SHA-256 checksum
     * @return True if valid and untampered
     */
    boolean verifyTicket(String ticketId, String verificationHash);
}
