package com.seatsync.client;

import com.seatsync.common.model.ErrorCode;
import com.seatsync.common.model.SeatStatus;
import com.seatsync.common.model.SeatView;
import com.seatsync.common.protocol.ReplyMessage;
import com.seatsync.common.protocol.event.SeatUpdatedEvent;
import com.seatsync.common.protocol.event.SeatHeldEvent;
import com.seatsync.common.protocol.event.SeatReleasedEvent;
import com.seatsync.common.protocol.reply.BookOkReply;
import com.seatsync.common.protocol.reply.BookRejectedReply;
import com.seatsync.common.protocol.reply.CancelOkReply;
import com.seatsync.common.protocol.reply.ErrorReply;
import com.seatsync.common.protocol.reply.SeatSnapshotReply;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ClientMessageHandler}.
 * Verifies dispatching of replies, correlating pending request futures,
 * and handling server-push broadcast events.
 * <p>
 * Owned by Member 01 (TCP Socket Programming & Desktop Client).
 */
class ClientMessageHandlerTest {

    private ClientMessageHandler handler;
    private ConcurrentMap<String, CompletableFuture<ReplyMessage>> pendingRequests;

    @BeforeEach
    void setUp() {
        handler = new ClientMessageHandler();
        pendingRequests = new ConcurrentHashMap<>();
    }

    @Test
    @DisplayName("Correlates BookOkReply with pending future and alerts listener")
    void testCorrelateBookOkReply() throws Exception {
        CompletableFuture<ReplyMessage> future = new CompletableFuture<>();
        pendingRequests.put("REQ-101", future);

        AtomicReference<BookOkReply> received = new AtomicReference<>();
        handler.addListener(new ClientMessageHandler.Listener() {
            @Override
            public void onBookSuccess(BookOkReply reply) {
                received.set(reply);
            }
        });

        String json = "{\"type\":\"BOOK_OK\",\"requestId\":\"REQ-101\",\"bookingId\":\"BKG-1\",\"showId\":\"SHOW-01\",\"seatId\":\"C4\",\"message\":\"Booked\"}";
        handler.handleIncomingLine(json, pendingRequests);

        assertTrue(future.isDone());
        ReplyMessage reply = future.get();
        assertEquals(BookOkReply.class, reply.getClass());

        BookOkReply ok = (BookOkReply) reply;
        assertEquals("BKG-1", ok.bookingId());
        assertEquals("C4", ok.seatId());
        assertNotNull(received.get());
        assertEquals("C4", received.get().seatId());
    }

    @Test
    @DisplayName("Dispatches asynchronous SeatUpdatedEvent broadcast to registered listeners")
    void testDispatchSeatUpdatedEvent() {
        AtomicReference<SeatUpdatedEvent> eventRef = new AtomicReference<>();
        handler.addListener(new ClientMessageHandler.Listener() {
            @Override
            public void onSeatUpdated(SeatUpdatedEvent event) {
                eventRef.set(event);
            }

            @Test
            @DisplayName("Dispatches hold and release events to the desktop presentation listener")
            void testDispatchHoldLifecycleEvents() {
                AtomicReference<SeatHeldEvent> held = new AtomicReference<>();
                AtomicReference<SeatReleasedEvent> released = new AtomicReference<>();
                handler.addListener(new ClientMessageHandler.Listener() {
                    @Override
                    public void onSeatHeld(SeatHeldEvent event) {
                        held.set(event);
                    }

                    @Override
                    public void onSeatReleased(SeatReleasedEvent event) {
                        released.set(event);
                    }
                });

                handler.handleIncomingLine(
                    "{\"type\":\"SEAT_HELD_EVENT\",\"showId\":\"SHOW-01\",\"seatId\":\"C4\",\"holdId\":\"HLD-1\",\"expiresAt\":2000,\"version\":2}",
                    pendingRequests);
                handler.handleIncomingLine(
                    "{\"type\":\"SEAT_RELEASED_EVENT\",\"showId\":\"SHOW-01\",\"seatId\":\"C4\",\"holdId\":\"HLD-1\",\"version\":3}",
                    pendingRequests);

                assertNotNull(held.get());
                assertEquals("C4", held.get().seatId());
                assertNotNull(released.get());
                assertEquals("HLD-1", released.get().holdId());
            }
        });

        String json = "{\"type\":\"SEAT_UPDATED\",\"showId\":\"SHOW-01\",\"version\":2,\"seats\":[{\"seatId\":\"A5\",\"status\":\"BOOKED\",\"bookedBy\":\"USR-102\"}]}";
        handler.handleIncomingLine(json, pendingRequests);

        assertNotNull(eventRef.get());
        assertEquals("SHOW-01", eventRef.get().showId());
        assertEquals(2L, eventRef.get().version());
        assertEquals(1, eventRef.get().seats().size());
        assertEquals("A5", eventRef.get().seats().get(0).seatId());
        assertEquals(SeatStatus.BOOKED, eventRef.get().seats().get(0).status());
    }

    @Test
    @DisplayName("Dispatches BookRejectedReply with ErrorCode")
    void testDispatchBookRejected() {
        AtomicReference<BookRejectedReply> ref = new AtomicReference<>();
        handler.addListener(new ClientMessageHandler.Listener() {
            @Override
            public void onBookRejected(BookRejectedReply reply) {
                ref.set(reply);
            }
        });

        String json = "{\"type\":\"BOOK_REJECTED\",\"requestId\":\"REQ-200\",\"errorCode\":\"SEAT_ALREADY_BOOKED\",\"message\":\"Already taken\"}";
        handler.handleIncomingLine(json, pendingRequests);

        assertNotNull(ref.get());
        assertEquals(ErrorCode.SEAT_ALREADY_BOOKED, ref.get().errorCode());
    }

    @Test
    @DisplayName("Dispatches CancelOkReply and SeatSnapshotReply")
    void testCancelAndSnapshot() {
        AtomicReference<CancelOkReply> cancelRef = new AtomicReference<>();
        AtomicReference<SeatSnapshotReply> snapshotRef = new AtomicReference<>();

        handler.addListener(new ClientMessageHandler.Listener() {
            @Override
            public void onCancelSuccess(CancelOkReply reply) {
                cancelRef.set(reply);
            }

            @Override
            public void onSeatSnapshot(SeatSnapshotReply reply) {
                snapshotRef.set(reply);
            }
        });

        String cancelJson = "{\"type\":\"CANCEL_OK\",\"requestId\":\"REQ-301\",\"bookingId\":\"BKG-99\",\"seatId\":\"D1\",\"message\":\"Cancelled\"}";
        handler.handleIncomingLine(cancelJson, pendingRequests);
        assertNotNull(cancelRef.get());
        assertEquals("BKG-99", cancelRef.get().bookingId());

        String snapJson = "{\"type\":\"SEAT_SNAPSHOT\",\"requestId\":\"REQ-302\",\"showId\":\"SHOW-01\",\"version\":1,\"seats\":[{\"seatId\":\"A1\",\"status\":\"AVAILABLE\"}]}";
        handler.handleIncomingLine(snapJson, pendingRequests);
        assertNotNull(snapshotRef.get());
        assertEquals(1, snapshotRef.get().seats().size());
    }
}
