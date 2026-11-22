package com.seatsync.common.model;

import java.util.Objects;

/**
 * Immutable administrative and security audit trail record.
 */
public record AuditLogEntry(
    String logId,
    long timestamp,
    String userId,
    String action,
    String resourceId,
    String detail,
    String ipAddress
) {
    public AuditLogEntry {
        Objects.requireNonNull(logId, "logId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(action, "action must not be null");
    }
}
