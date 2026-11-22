package com.seatsync.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Immutable point-in-time telemetry snapshot of NIO network counters and traffic observations.
 *
 * @param currentlyConnected     Current number of open TCP sessions
 * @param totalAccepted          Total accepted TCP connections since startup or reset
 * @param totalClosed            Total closed TCP connections since startup or reset
 * @param bytesIn                Total raw bytes read from the network
 * @param bytesOut               Total raw bytes written to the network
 * @param jsonFrames             Total valid UTF-8 JSON frames parsed
 * @param rejectedOversizedFrames Total frames rejected due to exceeding 16 KiB limit
 * @param eventBroadcastCount    Total server-pushed broadcast events emitted
 * @param averageConnectionAgeMs Average duration in milliseconds of closed connections
 * @param averageLatencyMs       Average request processing latency in milliseconds
 * @param p95LatencyMs           95th percentile request processing latency in milliseconds
 * @param timestamp              Epoch timestamp in milliseconds when snapshot was generated
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NetworkMetricsSnapshot(
    @JsonProperty("currentlyConnected") int currentlyConnected,
    @JsonProperty("totalAccepted") long totalAccepted,
    @JsonProperty("totalClosed") long totalClosed,
    @JsonProperty("bytesIn") long bytesIn,
    @JsonProperty("bytesOut") long bytesOut,
    @JsonProperty("jsonFrames") long jsonFrames,
    @JsonProperty("rejectedOversizedFrames") long rejectedOversizedFrames,
    @JsonProperty("eventBroadcastCount") long eventBroadcastCount,
    @JsonProperty("averageConnectionAgeMs") long averageConnectionAgeMs,
    @JsonProperty("averageLatencyMs") double averageLatencyMs,
    @JsonProperty("p95LatencyMs") double p95LatencyMs,
    @JsonProperty("timestamp") long timestamp
) {
}
