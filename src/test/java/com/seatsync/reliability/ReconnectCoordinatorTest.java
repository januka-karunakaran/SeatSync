package com.seatsync.reliability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReconnectCoordinator Unit Tests (U07)")
class ReconnectCoordinatorTest {

    private ReconnectCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new ReconnectCoordinator(100, 2.0, 5000, 5);
    }

    @Test
    @DisplayName("Accepts sequential version increments")
    void testSequentialVersionContinuity() {
        assertEquals(ReconnectCoordinator.EventAction.APPLY, coordinator.evaluateEventVersion("SHOW-01", 1L));
        assertEquals(ReconnectCoordinator.EventAction.APPLY, coordinator.evaluateEventVersion("SHOW-01", 2L));
        assertEquals(ReconnectCoordinator.EventAction.APPLY, coordinator.evaluateEventVersion("SHOW-01", 3L));
        assertEquals(3L, coordinator.getLastKnownVersion("SHOW-01"));
    }

    @Test
    @DisplayName("Discards stale or duplicate event versions")
    void testDiscardsStaleEvents() {
        coordinator.setSnapshotVersion("SHOW-01", 10L);

        // Same version -> discard
        assertEquals(ReconnectCoordinator.EventAction.DISCARD_STALE, coordinator.evaluateEventVersion("SHOW-01", 10L));
        // Older version -> discard
        assertEquals(ReconnectCoordinator.EventAction.DISCARD_STALE, coordinator.evaluateEventVersion("SHOW-01", 9L));
        assertEquals(ReconnectCoordinator.EventAction.DISCARD_STALE, coordinator.evaluateEventVersion("SHOW-01", 1L));
    }

    @Test
    @DisplayName("Detects version gap and requests snapshot resync")
    void testDetectsVersionGap() {
        coordinator.setSnapshotVersion("SHOW-01", 10L);

        // Version jump from 10 to 13 indicates missed network packets during a disconnect
        assertEquals(ReconnectCoordinator.EventAction.GAP_RESYNC_REQUIRED, coordinator.evaluateEventVersion("SHOW-01", 13L));
    }

    @Test
    @DisplayName("Calculates exponential backoff capped at maxBackoff")
    void testExponentialBackoff() {
        assertEquals(100, coordinator.calculateBackoff(0));
        assertEquals(200, coordinator.calculateBackoff(1));
        assertEquals(400, coordinator.calculateBackoff(2));
        assertEquals(800, coordinator.calculateBackoff(3));
        assertEquals(1600, coordinator.calculateBackoff(4));
        assertEquals(3200, coordinator.calculateBackoff(5));
        assertEquals(5000, coordinator.calculateBackoff(6)); // Capped at max 5000ms
        assertEquals(5000, coordinator.calculateBackoff(10));
    }

    @Test
    @DisplayName("Tracks and restores show subscriptions across reconnections")
    void testTracksShowSubscriptions() {
        coordinator.trackShowSubscription("SHOW-01");
        coordinator.trackShowSubscription("SHOW-02");

        assertTrue(coordinator.getTrackedSubscriptions().contains("SHOW-01"));
        assertTrue(coordinator.getTrackedSubscriptions().contains("SHOW-02"));

        coordinator.untrackShowSubscription("SHOW-01");
        assertFalse(coordinator.getTrackedSubscriptions().contains("SHOW-01"));
        assertTrue(coordinator.getTrackedSubscriptions().contains("SHOW-02"));
    }
}
