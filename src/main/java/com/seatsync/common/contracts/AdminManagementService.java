package com.seatsync.common.contracts;

import com.seatsync.common.model.AuditLogEntry;
import com.seatsync.common.model.SeatStatus;
import com.seatsync.common.model.ShowDetails;

import java.util.List;

/**
 * Service contract for administrative management operations.
 * Requires caller to hold {@link com.seatsync.common.model.UserRole#ADMIN} privileges.
 */
public interface AdminManagementService {

    /**
     * Adds a new show and auditorium layout to the catalog.
     *
     * @param adminUserId Calling user ID (must be ADMIN)
     * @param showDetails Show definition
     * @return True if show was added
     */
    boolean addShow(String adminUserId, ShowDetails showDetails);

    /**
     * Administratively overrides seat status (e.g. maintenance, vip hold).
     *
     * @param adminUserId Calling user ID (must be ADMIN)
     * @param showId      Target show ID
     * @param seatId      Target seat ID
     * @param status      New status
     * @return True if updated
     */
    boolean updateSeatStatus(String adminUserId, String showId, String seatId, SeatStatus status);

    /**
     * Forces emergency cancellation of an active seat hold.
     *
     * @param adminUserId Calling user ID (must be ADMIN)
     * @param holdId      Target hold ID
     * @param reason      Administrative reason for audit logging
     * @return True if cancelled
     */
    boolean cancelHold(String adminUserId, String holdId, String reason);

    /**
     * Retrieves administrative audit logs with limit.
     *
     * @param adminUserId Calling user ID (must be ADMIN)
     * @param limit       Maximum entries to retrieve
     * @return List of audit entries
     */
    List<AuditLogEntry> getAuditLogs(String adminUserId, int limit);
}
