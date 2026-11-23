package com.seatsync.reliability;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Client-side connection recovery, version gap detection, and request resynchronization coordinator.
 * Features:
 * <ul>
 *   <li>Exponential backoff reconnect loop with configurable max attempts.</li>
 *   <li>Monotonic event version gap detection triggering authoritative seat snapshot resync.</li>
 *   <li>Discarding stale or out-of-order event broadcasts.</li>
 *   <li>Restoration of show subscriptions and authenticated sessions post-reconnect.</li>
 * </ul>
 * <p>
 * Owned by Member 01 (Reliability, Events &amp; Asynchronous Messaging).
 */
public class ReconnectCoordinator {

    private static final Logger LOG = Logger.getLogger(ReconnectCoordinator.class.getName());

    public enum EventAction {
        APPLY,
        DISCARD_STALE,
        GAP_RESYNC_REQUIRED
    }

    public static final long DEFAULT_INITIAL_BACKOFF_MS = 100;
    public static final double DEFAULT_BACKOFF_MULTIPLIER = 2.0;
    public static final long DEFAULT_MAX_BACKOFF_MS = 5000;
    public static final int DEFAULT_MAX_RETRIES = 5;

    private final long initialBackoffMs;
    private final double backoffMultiplier;
    private final long maxBackoffMs;
    private final int maxRetries;

    // Track latest known seat map version per show
    private final Map<String, Long> lastKnownVersions = new ConcurrentHashMap<>();

    // Track active show subscriptions to restore on reconnect
    private final Set<String> subscribedShows = new CopyOnWriteArraySet<>();

    public ReconnectCoordinator() {
        this(DEFAULT_INITIAL_BACKOFF_MS, DEFAULT_BACKOFF_MULTIPLIER, DEFAULT_MAX_BACKOFF_MS, DEFAULT_MAX_RETRIES);
    }

    public ReconnectCoordinator(long initialBackoffMs, double backoffMultiplier, long maxBackoffMs, int maxRetries) {
        this.initialBackoffMs = initialBackoffMs;
        this.backoffMultiplier = backoffMultiplier;
        this.maxBackoffMs = maxBackoffMs;
        this.maxRetries = maxRetries;
    }

    /**
     * Records a show subscription to be restored upon reconnection.
     */
    public void trackShowSubscription(String showId) {
        if (showId != null) {
            subscribedShows.add(showId);
        }
    }

    /**
     * Unregisters a show subscription from reconnect tracking.
     */
    public void untrackShowSubscription(String showId) {
        if (showId != null) {
            subscribedShows.remove(showId);
        }
    }

    public Set<String> getTrackedSubscriptions() {
        return Collections.unmodifiableSet(subscribedShows);
    }

    /**
     * Sets the authoritative baseline version for a show (e.g. from SeatSnapshotReply).
     */
    public void setSnapshotVersion(String showId, long version) {
        if (showId != null) {
            lastKnownVersions.put(showId, version);
        }
    }

    public Long getLastKnownVersion(String showId) {
        return lastKnownVersions.get(showId);
    }

    /**
     * Evaluates an incoming event version against the show's revision history.
     *
     * @param showId       Show ID
     * @param eventVersion Incoming event revision version
     * @return EventAction indicating whether to APPLY, DISCARD_STALE, or GAP_RESYNC_REQUIRED
     */
    public EventAction evaluateEventVersion(String showId, long eventVersion) {
        if (showId == null) {
            return EventAction.DISCARD_STALE;
        }

        Long last = lastKnownVersions.get(showId);
        if (last == null) {
            // First time seeing this show: accept and record
            lastKnownVersions.put(showId, eventVersion);
            return EventAction.APPLY;
        }

        if (eventVersion <= last) {
            // Stale or duplicate event: discard
            LOG.info(() -> "Discarding stale event for " + showId + ": version " + eventVersion + " <= " + last);
            return EventAction.DISCARD_STALE;
        }

        if (eventVersion == last + 1) {
            // Perfect sequential continuity
            lastKnownVersions.put(showId, eventVersion);
            return EventAction.APPLY;
        }

        // eventVersion > last + 1: GAP DETECTED!
        // Messages were lost (e.g. during a network drop); force snapshot resync
        LOG.warning(() -> "Event version gap detected for " + showId + "! Last known: " + last
            + ", received: " + eventVersion + ". Requesting snapshot resync.");
        return EventAction.GAP_RESYNC_REQUIRED;
    }

    /**
     * Calculates the exponential backoff duration in milliseconds for a retry attempt.
     *
     * @param attempt Zero-based attempt index
     * @return Backoff delay in milliseconds
     */
    public long calculateBackoff(int attempt) {
        if (attempt <= 0) {
            return initialBackoffMs;
        }
        double delay = initialBackoffMs * Math.pow(backoffMultiplier, attempt);
        return Math.min((long) delay, maxBackoffMs);
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void clear() {
        lastKnownVersions.clear();
        subscribedShows.clear();
    }
}
