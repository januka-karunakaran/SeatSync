package com.seatsync.delivery;

import com.seatsync.common.contracts.RequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ShowSubscriptionRegistry Unit Tests (U06)")
class ShowSubscriptionRegistryTest {

    private ShowSubscriptionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ShowSubscriptionRegistry();
    }

    private static class MockContext implements RequestContext {
        private final String sessionId;
        private final AtomicBoolean open = new AtomicBoolean(true);

        MockContext(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override public String getSessionId() { return sessionId; }
        @Override public Optional<String> getAuthenticatedUserId() { return Optional.of("user-" + sessionId); }
        @Override public void setAuthenticatedUserId(String userId) {}
        @Override public void clearAuthentication() {}
        @Override public boolean isOpen() { return open.get(); }
        @Override public void close() { open.set(false); }
        @Override public void send(String jsonLine) {}
    }

    @Test
    @DisplayName("Should successfully subscribe and report isSubscribed")
    void testSubscribeAndIsSubscribed() {
        registry.subscribe("sess-1", "SHOW-01");
        assertTrue(registry.isSubscribed("sess-1", "SHOW-01"));
        assertFalse(registry.isSubscribed("sess-1", "SHOW-02"));
        assertFalse(registry.isSubscribed("sess-2", "SHOW-01"));
    }

    @Test
    @DisplayName("Should unsubscribe correctly and clean up empty show sets")
    void testUnsubscribe() {
        registry.subscribe("sess-1", "SHOW-01");
        registry.subscribe("sess-2", "SHOW-01");

        registry.unsubscribe("sess-1", "SHOW-01");
        assertFalse(registry.isSubscribed("sess-1", "SHOW-01"));
        assertTrue(registry.isSubscribed("sess-2", "SHOW-01"));

        registry.unsubscribe("sess-2", "SHOW-01");
        assertFalse(registry.isSubscribed("sess-2", "SHOW-01"));
        assertEquals(0, registry.totalSubscriptionsCount());
    }

    @Test
    @DisplayName("Should retrieve active subscribers and prune closed sessions")
    void testGetSubscribersPrunesClosedSessions() {
        MockContext c1 = new MockContext("sess-1");
        MockContext c2 = new MockContext("sess-2");
        registry.registerContext(c1);
        registry.registerContext(c2);

        registry.subscribe("sess-1", "SHOW-01");
        registry.subscribe("sess-2", "SHOW-01");

        Collection<RequestContext> subscribers = registry.getSubscribers("SHOW-01");
        assertEquals(2, subscribers.size());

        // Close c2
        c2.close();

        // Calling getSubscribers should evict c2
        Collection<RequestContext> afterClose = registry.getSubscribers("SHOW-01");
        assertEquals(1, afterClose.size());
        assertEquals("sess-1", afterClose.iterator().next().getSessionId());
        assertFalse(registry.isSubscribed("sess-2", "SHOW-01"));
    }

    @Test
    @DisplayName("Should remove all subscriptions on session disconnect")
    void testRemoveSession() {
        registry.subscribe("sess-1", "SHOW-01");
        registry.subscribe("sess-1", "SHOW-02");
        registry.subscribe("sess-2", "SHOW-01");

        assertEquals(3, registry.totalSubscriptionsCount());

        registry.removeSession("sess-1");

        assertFalse(registry.isSubscribed("sess-1", "SHOW-01"));
        assertFalse(registry.isSubscribed("sess-1", "SHOW-02"));
        assertTrue(registry.isSubscribed("sess-2", "SHOW-01"));
        assertEquals(1, registry.totalSubscriptionsCount());
    }
}
