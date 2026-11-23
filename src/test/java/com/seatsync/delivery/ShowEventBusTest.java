package com.seatsync.delivery;

import com.seatsync.common.contracts.RequestContext;
import com.seatsync.common.model.SeatStatus;
import com.seatsync.common.model.SeatView;
import com.seatsync.common.model.ShowSummary;
import com.seatsync.common.protocol.Message;
import com.seatsync.common.protocol.ProtocolJson;
import com.seatsync.common.protocol.event.SeatHeldEvent;
import com.seatsync.common.protocol.event.SeatReleasedEvent;
import com.seatsync.common.protocol.event.SeatUpdatedEvent;
import com.seatsync.common.protocol.event.ShowUpdatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ShowEventBus Unit & Isolation Tests (U06)")
class ShowEventBusTest {

    private ShowSubscriptionRegistry registry;
    private ShowEventBus eventBus;

    private static class RecordingContext implements RequestContext {
        final String sessionId;
        final String userId;
        final List<String> receivedLines = new CopyOnWriteArrayList<>();
        final AtomicBoolean open = new AtomicBoolean(true);

        RecordingContext(String sessionId, String userId) {
            this.sessionId = sessionId;
            this.userId = userId;
        }

        @Override public String getSessionId() { return sessionId; }
        @Override public Optional<String> getAuthenticatedUserId() { return Optional.ofNullable(userId); }
        @Override public void setAuthenticatedUserId(String userId) {}
        @Override public void clearAuthentication() {}
        @Override public boolean isOpen() { return open.get(); }
        @Override public void close() { open.set(false); }
        @Override public void send(String jsonLine) {
            if (open.get() && jsonLine != null) {
                receivedLines.add(jsonLine);
            }
        }
    }

    @BeforeEach
    void setUp() {
        registry = new ShowSubscriptionRegistry();
        eventBus = new ShowEventBus(registry);
    }

    @Test
    @DisplayName("Demonstrates 3 clients, same-show delivery and unrelated show isolation")
    void testThreeClientsShowIsolation() throws Exception {
        // 3 clients:
        // Client 1 & Client 2 subscribed to SHOW-01
        // Client 3 subscribed to SHOW-02
        RecordingContext client1 = new RecordingContext("sess-1", "user-alice");
        RecordingContext client2 = new RecordingContext("sess-2", "user-bob");
        RecordingContext client3 = new RecordingContext("sess-3", "user-charlie");

        registry.registerContext(client1);
        registry.registerContext(client2);
        registry.registerContext(client3);

        registry.subscribe("sess-1", "SHOW-01");
        registry.subscribe("sess-2", "SHOW-01");
        registry.subscribe("sess-3", "SHOW-02");

        // Broadcast event on SHOW-01
        List<SeatView> seatsShow1 = List.of(
            new SeatView("A1", SeatStatus.HELD, null),
            new SeatView("A2", SeatStatus.AVAILABLE, null)
        );
        eventBus.publishSeatUpdated("SHOW-01", 10L, seatsShow1);

        // Verify: Client 1 and 2 received SHOW-01 update
        assertEquals(1, client1.receivedLines.size());
        assertEquals(1, client2.receivedLines.size());
        Message msg1 = ProtocolJson.deserializeMessage(client1.receivedLines.get(0));
        assertTrue(msg1 instanceof SeatUpdatedEvent);
        SeatUpdatedEvent ev1 = (SeatUpdatedEvent) msg1;
        assertEquals("SHOW-01", ev1.showId());
        assertEquals(10L, ev1.version());
        assertEquals(2, ev1.seats().size());

        // Verify: Client 3 received NOTHING on SHOW-02 (Show Isolation verified!)
        assertEquals(0, client3.receivedLines.size());

        // Now broadcast event on SHOW-02
        List<SeatView> seatsShow2 = List.of(
            new SeatView("B1", SeatStatus.BOOKED, null)
        );
        eventBus.publishSeatUpdated("SHOW-02", 5L, seatsShow2);

        // Client 3 now receives the SHOW-02 update
        assertEquals(1, client3.receivedLines.size());
        Message msg3 = ProtocolJson.deserializeMessage(client3.receivedLines.get(0));
        assertTrue(msg3 instanceof SeatUpdatedEvent);
        SeatUpdatedEvent ev3 = (SeatUpdatedEvent) msg3;
        assertEquals("SHOW-02", ev3.showId());
        assertEquals(5L, ev3.version());

        // Client 1 and 2 still only have 1 message (they did not receive SHOW-02)
        assertEquals(1, client1.receivedLines.size());
        assertEquals(1, client2.receivedLines.size());
    }

    @Test
    @DisplayName("Publishes SEAT_HELD and SEAT_RELEASED events with versioning")
    void testPublishSeatHeldAndReleased() throws Exception {
        RecordingContext client = new RecordingContext("sess-1", "user-1");
        registry.registerContext(client);
        registry.subscribe("sess-1", "SHOW-01");

        long expiresAt = System.currentTimeMillis() + 120_000;
        eventBus.publishSeatHeld("SHOW-01", "C3", "HLD-999", expiresAt, 15L);
        eventBus.publishSeatReleased("SHOW-01", "C3", "HLD-999", 16L);

        assertEquals(2, client.receivedLines.size());

        Message m1 = ProtocolJson.deserializeMessage(client.receivedLines.get(0));
        assertTrue(m1 instanceof SeatHeldEvent);
        SeatHeldEvent held = (SeatHeldEvent) m1;
        assertEquals("C3", held.seatId());
        assertEquals("HLD-999", held.holdId());
        assertEquals(15L, held.version());

        Message m2 = ProtocolJson.deserializeMessage(client.receivedLines.get(1));
        assertTrue(m2 instanceof SeatReleasedEvent);
        SeatReleasedEvent released = (SeatReleasedEvent) m2;
        assertEquals("C3", released.seatId());
        assertEquals(16L, released.version());
    }

    @Test
    @DisplayName("Publishes SHOW_UPDATED event to subscribers")
    void testPublishShowUpdated() throws Exception {
        RecordingContext client = new RecordingContext("sess-1", "user-1");
        registry.registerContext(client);
        registry.subscribe("sess-1", "SHOW-01");

        ShowSummary summary = new ShowSummary("SHOW-01", "Inception", "2026-11-25T18:00:00Z", "Main Hall", 40, 40);
        eventBus.publishShowUpdated("SHOW-01", summary, 2L, true);

        assertEquals(1, client.receivedLines.size());
        Message msg = ProtocolJson.deserializeMessage(client.receivedLines.get(0));
        assertTrue(msg instanceof ShowUpdatedEvent);
        ShowUpdatedEvent ev = (ShowUpdatedEvent) msg;
        assertEquals("SHOW-01", ev.showId());
        assertEquals(2L, ev.version());
        assertTrue(ev.enabled());
    }

    @Test
    @DisplayName("Guarantees sanitized payloads without leaking sensitive tokens")
    void testSanitizedPayloads() throws Exception {
        RecordingContext client = new RecordingContext("sess-1", "user-1");
        registry.registerContext(client);
        registry.subscribe("sess-1", "SHOW-01");

        // Public broadcast of seat views
        List<SeatView> seats = List.of(new SeatView("D4", SeatStatus.AVAILABLE, null));
        eventBus.publishSeatUpdated("SHOW-01", 1L, seats);

        String json = client.receivedLines.get(0);
        assertFalse(json.contains("password"));
        assertFalse(json.contains("secret"));
        assertFalse(json.contains("token"));
    }

    @Test
    @DisplayName("Gracefully ignores disconnected clients and prunes them")
    void testDisconnectedClientDoesNotThrow() {
        RecordingContext closedClient = new RecordingContext("sess-dead", "user-dead");
        closedClient.close(); // Closed socket

        registry.registerContext(closedClient);
        registry.subscribe("sess-dead", "SHOW-01");

        // Should not throw exception
        assertDoesNotThrow(() -> {
            eventBus.publishSeatUpdated("SHOW-01", 1L, List.of(SeatView.available("A1")));
        });

        // Dead subscriber should have been evicted
        assertFalse(registry.isSubscribed("sess-dead", "SHOW-01"));
    }
}
