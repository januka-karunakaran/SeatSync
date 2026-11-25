package com.seatsync.common.contracts;

import com.seatsync.common.model.BookingResult;
import com.seatsync.common.model.HoldResult;
import com.seatsync.common.model.MultiHoldResult;
import com.seatsync.common.model.ReleaseResult;
import com.seatsync.common.model.SeatHold;

import java.util.List;
import java.util.Optional;

/**
 * Service contract for two-phase seat holds with TTL expiration and idempotent confirmation.
 */
public interface HoldService {

    /**
     * Attempts to place a temporary hold on a seat for an authenticated user.
     *
     * @param userId     Authenticated user identifier
     * @param showId     Target show ID
     * @param seatId     Target seat ID
     * @param durationMs Desired hold duration in milliseconds (capped by server policy)
     * @return Result of the hold attempt
     */
    HoldResult holdSeat(String userId, String showId, String seatId, long durationMs);

    /**
     * Holds every listed seat or none of them. A failure leaves the show unchanged.
     */
    MultiHoldResult holdSeats(String userId, String showId, List<String> seatIds, long durationMs);

    /**
     * Releases an active hold prior to TTL expiration.
     *
     * @param userId Authenticated user identifier (must be the hold owner)
     * @param holdId Unique hold identifier
     * @return Result of the release attempt
     */
    ReleaseResult releaseHold(String userId, String holdId);

    /**
     * Atomically confirms a hold, transitioning the seat to permanent BOOKED status.
     *
     * @param userId         Authenticated user identifier
     * @param holdId         Unique hold identifier
     * @param idempotencyKey Client-supplied unique token preventing duplicate charges/bookings
     * @return Result of the confirmation
     */
    BookingResult confirmHold(String userId, String holdId, String idempotencyKey);

    /**
     * Retrieves hold information by ID.
     *
     * @param holdId Hold identifier
     * @return Optional containing SeatHold if found
     */
    Optional<SeatHold> getHold(String holdId);

    /**
     * Retrieves all active holds for a show.
     *
     * @param showId Show identifier
     * @return List of active holds
     */
    List<SeatHold> getActiveHolds(String showId);
}
