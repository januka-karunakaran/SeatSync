package com.seatsync.common.contracts;

import com.seatsync.common.model.ServerMetricSnapshot;

/**
 * Service contract for collecting and reporting real-time server telemetry.
 */
public interface MetricsService {

    /**
     * @return Current immutable server metrics snapshot
     */
    ServerMetricSnapshot getMetricsSnapshot();

    /**
     * Records request duration and completion status.
     *
     * @param messageType   Request type name
     * @param durationNanos Processing duration in nanoseconds
     * @param success       True if processed without error
     */
    void recordRequest(String messageType, long durationNanos, boolean success);
}
