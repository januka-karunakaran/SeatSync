package com.seatsync.security;

import com.seatsync.common.model.AuditLogEntry;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Bounded in-memory metadata-only audit log. Never accepts raw payloads, reasons or secrets. */
public final class SecurityAuditLog {
    public enum Outcome { SUCCESS, DENIED, INVALID, UNAVAILABLE, FAILURE }
    private final Clock clock;
    private final int capacity;
    private final ArrayDeque<AuditLogEntry> entries = new ArrayDeque<>();

    public SecurityAuditLog() { this(Clock.systemUTC(), 1000); }
    public SecurityAuditLog(Clock clock, int capacity) {
        this.clock = Objects.requireNonNull(clock);
        if (capacity < 1 || capacity > 10000) { throw new IllegalArgumentException("Invalid audit capacity"); }
        this.capacity = capacity;
    }

    public synchronized void record(String actor, String action, String requestId, Outcome outcome) {
        Objects.requireNonNull(outcome);
        if (entries.size() == capacity) { entries.removeFirst(); }
        // Only bounded identifiers are retained. Untrusted free text is discarded, not regex-redacted.
        entries.addLast(new AuditLogEntry(UUID.randomUUID().toString(), clock.millis(), safe(actor), safe(action),
            null, "outcome=" + outcome + ";requestId=" + safe(requestId), null));
    }

    public synchronized List<AuditLogEntry> recent(int limit) {
        if (limit < 1 || limit > 20) { throw new IllegalArgumentException("Audit limit must be 1 to 20"); }
        return entries.stream().skip(Math.max(0, entries.size() - limit)).toList();
    }

    private static String safe(String value) {
        return value != null && value.matches("[A-Za-z0-9._-]{1,128}") ? value : "N-A";
    }
}
