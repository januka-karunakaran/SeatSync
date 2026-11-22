package com.seatsync.network.metrics;

import com.seatsync.common.contracts.MetricsService;
import com.seatsync.common.model.NetworkMetricsSnapshot;
import com.seatsync.common.model.ServerMetricSnapshot;

import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Non-blocking, thread-safe metrics tracker instrumenting the NIO selector and session lifecycle.
 * Uses lock-free {@link LongAdder} and atomic accumulators to eliminate contention under high concurrency.
 * <p>
 * Implements {@link MetricsService} to provide a unified telemetry interface.
 */
public class NetworkMetricsTracker implements MetricsService {

    private final LongAdder totalAccepted = new LongAdder();
    private final LongAdder totalClosed = new LongAdder();
    private final LongAdder bytesIn = new LongAdder();
    private final LongAdder bytesOut = new LongAdder();
    private final LongAdder jsonFrames = new LongAdder();
    private final LongAdder rejectedOversizedFrames = new LongAdder();
    private final LongAdder eventBroadcastCount = new LongAdder();
    private final LongAdder totalClosedDurationMs = new LongAdder();
    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder errorCount = new LongAdder();

    // Bounded reservoir of recent latency samples (in microseconds) for p95 calculation
    private static final int MAX_LATENCY_SAMPLES = 1000;
    private final AtomicLong[] latencySamples = new AtomicLong[MAX_LATENCY_SAMPLES];
    private final AtomicInteger sampleIndex = new AtomicInteger(0);

    private final long startupTimeMs;

    public NetworkMetricsTracker() {
        this.startupTimeMs = System.currentTimeMillis();
        for (int i = 0; i < MAX_LATENCY_SAMPLES; i++) {
            latencySamples[i] = new AtomicLong(0);
        }
    }

    public void recordAcceptedConnection() {
        totalAccepted.increment();
    }

    public void recordClosedConnection(long durationMs) {
        totalClosed.increment();
        if (durationMs > 0) {
            totalClosedDurationMs.add(durationMs);
        }
    }

    public void recordBytesIn(long bytes) {
        if (bytes > 0) {
            bytesIn.add(bytes);
        }
    }

    public void recordBytesOut(long bytes) {
        if (bytes > 0) {
            bytesOut.add(bytes);
        }
    }

    public void recordJsonFrame() {
        jsonFrames.increment();
    }

    public void recordRejectedFrame() {
        rejectedOversizedFrames.increment();
        errorCount.increment();
    }

    public void recordBroadcast() {
        eventBroadcastCount.increment();
    }

    public void recordRequestLatency(long durationNanos) {
        long micros = durationNanos / 1000L;
        int idx = (sampleIndex.getAndIncrement() & Integer.MAX_VALUE) % MAX_LATENCY_SAMPLES;
        latencySamples[idx].set(micros);
    }

    @Override
    public void recordRequest(String messageType, long durationNanos, boolean success) {
        totalRequests.increment();
        recordRequestLatency(durationNanos);
        if (!success) {
            errorCount.increment();
        }
    }

    /**
     * Produces an immutable snapshot of detailed network metrics.
     */
    public NetworkMetricsSnapshot getNetworkMetricsSnapshot() {
        long accepted = totalAccepted.sum();
        long closed = totalClosed.sum();
        int active = (int) Math.max(0, accepted - closed);
        long closedDuration = totalClosedDurationMs.sum();
        long avgAge = closed > 0 ? closedDuration / closed : 0;

        LatencyStats stats = calculateLatencyStats();

        return new NetworkMetricsSnapshot(
            active,
            accepted,
            closed,
            bytesIn.sum(),
            bytesOut.sum(),
            jsonFrames.sum(),
            rejectedOversizedFrames.sum(),
            eventBroadcastCount.sum(),
            avgAge,
            stats.averageMs(),
            stats.p95Ms(),
            System.currentTimeMillis()
        );
    }

    @Override
    public ServerMetricSnapshot getMetricsSnapshot() {
        long accepted = totalAccepted.sum();
        long closed = totalClosed.sum();
        int active = (int) Math.max(0, accepted - closed);
        long uptime = Math.max(0, (System.currentTimeMillis() - startupTimeMs) / 1000L);
        LatencyStats stats = calculateLatencyStats();

        return new ServerMetricSnapshot(
            uptime,
            active,
            totalRequests.sum(),
            0L, // total bookings tracked by booking engine
            0,  // holds tracked by booking engine
            0,  // waitlist tracked by booking engine
            stats.p95Ms(),
            errorCount.sum()
        );
    }

    /**
     * Resets all metric counters (useful for unit testing and baseline benchmarking).
     */
    public void reset() {
        totalAccepted.reset();
        totalClosed.reset();
        bytesIn.reset();
        bytesOut.reset();
        jsonFrames.reset();
        rejectedOversizedFrames.reset();
        eventBroadcastCount.reset();
        totalClosedDurationMs.reset();
        totalRequests.reset();
        errorCount.reset();
        sampleIndex.set(0);
        for (int i = 0; i < MAX_LATENCY_SAMPLES; i++) {
            latencySamples[i].set(0);
        }
    }

    private LatencyStats calculateLatencyStats() {
        int count = Math.min(sampleIndex.get(), MAX_LATENCY_SAMPLES);
        if (count <= 0) {
            return new LatencyStats(0.0, 0.0);
        }

        long[] copy = new long[count];
        long sum = 0;
        for (int i = 0; i < count; i++) {
            copy[i] = latencySamples[i].get();
            sum += copy[i];
        }

        Arrays.sort(copy);
        double avgMs = (sum / (double) count) / 1000.0;
        int p95Index = (int) Math.ceil(0.95 * count) - 1;
        p95Index = Math.max(0, Math.min(count - 1, p95Index));
        double p95Ms = copy[p95Index] / 1000.0;

        return new LatencyStats(avgMs, p95Ms);
    }

    private record LatencyStats(double averageMs, double p95Ms) {}
}
