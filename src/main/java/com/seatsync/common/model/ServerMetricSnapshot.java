package com.seatsync.common.model;

/**
 * Point-in-time telemetry snapshot of server metrics and health indicators.
 */
public record ServerMetricSnapshot(
    long uptimeSeconds,
    int activeConnections,
    long totalRequests,
    long totalBookings,
    int activeHolds,
    int waitlistSize,
    double p95LatencyMs,
    long errorCount
) {
}
