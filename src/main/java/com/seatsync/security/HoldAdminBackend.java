package com.seatsync.security;

import com.seatsync.common.contracts.AdminManagementService;
import com.seatsync.common.contracts.HoldService;
import com.seatsync.common.model.AuditLogEntry;
import com.seatsync.common.model.SeatStatus;
import com.seatsync.common.model.ShowDetails;
import java.util.List;
import java.util.Objects;

/** Bridges an authorized admin cancellation to M3's atomic hold release, including FIFO promotion. */
public final class HoldAdminBackend implements AdminManagementService {
    private final AuthorizationService authorization;
    private final HoldService holds;
    public HoldAdminBackend(AuthorizationService authorization, HoldService holds) {
        this.authorization = Objects.requireNonNull(authorization);
        this.holds = Objects.requireNonNull(holds);
    }
    @Override public boolean cancelHold(String actor, String holdId, String reason) {
        authorization.requireAdmin(actor);
        if (holds instanceof com.seatsync.common.contracts.AdminCatalogOperations operations) {
            return operations.forceCancelHold(holdId);
        }
        // Owner is read from M3's trusted record, never supplied in the admin request.
        return holds.getHold(holdId).map(hold -> holds.releaseHold(hold.heldByUserId(), hold.holdId()).success()).orElse(false);
    }
    @Override public boolean addShow(String actor, ShowDetails show) {
        authorization.requireAdmin(actor);
        throw new UnsupportedOperationException("Catalog mutations are handled by AdminService");
    }
    @Override public boolean updateSeatStatus(String actor, String show, String seat, SeatStatus status) {
        authorization.requireAdmin(actor);
        if (holds instanceof com.seatsync.common.contracts.AdminCatalogOperations operations) {
            return operations.overrideSeat(actor, show, seat, status);
        }
        throw new UnsupportedOperationException("Atomic seat overrides are not exposed by the booking backend");
    }
    @Override public List<AuditLogEntry> getAuditLogs(String actor, int limit) {
        authorization.requireAdmin(actor);
        throw new UnsupportedOperationException("Audit reads are handled by AdminService");
    }
}
