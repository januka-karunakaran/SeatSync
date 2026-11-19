package com.seatsync.common.contracts;

import com.seatsync.common.model.BookingResult;
import com.seatsync.common.model.CancelResult;
import com.seatsync.common.model.SeatSnapshot;

/**
 * Core reservation service interface managing thread-safe seat bookings,
 * cancellations, and point-in-time snapshots for shows.
 */
public interface ReservationService {

    /**
     * Attempts to book a specific seat for an authenticated user.
     *
     * @param userId The authenticated user ID making the booking (must not be null)
     * @param showId Show identifier (e.g. SHOW-01)
     * @param seatId Seat identifier (e.g. A1-E8)
     * @return Result of the booking transaction
     */
    BookingResult book(String userId, String showId, String seatId);

    /**
     * Attempts to cancel an existing booking on behalf of an authenticated user.
     * The implementation must verify that the booking belongs to the caller.
     *
     * @param userId    The authenticated user ID requesting cancellation
     * @param bookingId Booking identifier to cancel
     * @return Result of the cancellation transaction
     */
    CancelResult cancel(String userId, String bookingId);

    /**
     * Generates a point-in-time immutable snapshot of all seat allocations for a show.
     *
     * @param showId Show identifier
     * @return Current seat snapshot
     */
    SeatSnapshot snapshot(String showId);
}
