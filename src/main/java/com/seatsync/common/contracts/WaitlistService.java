package com.seatsync.common.contracts;

import com.seatsync.common.model.WaitlistEntry;
import com.seatsync.common.model.WaitlistResult;

import java.util.Optional;

/**
 * Service contract for sold-out show FIFO waitlists.
 */
public interface WaitlistService {

    /**
     * Enqueues an authenticated user to the FIFO waitlist for a show.
     *
     * @param userId Authenticated user identifier
     * @param showId Target show ID
     * @return Result of the waitlist join attempt
     */
    WaitlistResult joinWaitlist(String userId, String showId);

    /**
     * Removes an authenticated user from the FIFO waitlist.
     *
     * @param userId Authenticated user identifier
     * @param showId Target show ID
     * @return Result of the waitlist leave attempt
     */
    WaitlistResult leaveWaitlist(String userId, String showId);

    /**
     * Retrieves the current waitlist status for a user.
     *
     * @param userId Authenticated user identifier
     * @param showId Target show ID
     * @return Optional containing WaitlistEntry with queue position
     */
    Optional<WaitlistEntry> getWaitlistStatus(String userId, String showId);

    /**
     * @return Current 1-based queue position for the user, or -1 if not enqueued
     */
    int getQueuePosition(String userId, String showId);
}
