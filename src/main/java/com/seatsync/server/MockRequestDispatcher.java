package com.seatsync.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.seatsync.common.contracts.RequestContext;
import com.seatsync.common.contracts.RequestDispatcher;
import com.seatsync.common.model.ErrorCode;
import com.seatsync.common.model.ProtocolConstants;
import com.seatsync.common.model.SeatStatus;
import com.seatsync.common.model.SeatView;
import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.ProtocolJson;
import com.seatsync.common.protocol.event.SeatUpdatedEvent;
import com.seatsync.common.protocol.reply.BookOkReply;
import com.seatsync.common.protocol.reply.BookRejectedReply;
import com.seatsync.common.protocol.reply.CancelOkReply;
import com.seatsync.common.protocol.reply.CancelRejectedReply;
import com.seatsync.common.protocol.reply.ErrorReply;
import com.seatsync.common.protocol.reply.LoginOkReply;
import com.seatsync.common.protocol.reply.LogoutOkReply;
import com.seatsync.common.protocol.reply.SeatSnapshotReply;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Temporary mock implementation of {@link RequestDispatcher} for Stage A.
 * Maintains in-memory seat state across all connected clients and broadcasts
 * asynchronous seat change events (SEAT_UPDATED) to all active sessions.
 */
public class MockRequestDispatcher implements RequestDispatcher {

    private final Map<String, SeatView> seatState = new ConcurrentHashMap<>(40);
    private final Map<String, String> bookingToSeat = new ConcurrentHashMap<>();
    private final Map<String, String> seatToBooking = new ConcurrentHashMap<>();
    private final Map<String, String> bookingOwners = new ConcurrentHashMap<>();
    private final AtomicLong version = new AtomicLong(1);
    private Consumer<String> broadcaster;

    public MockRequestDispatcher() {
        for (String seatId : ProtocolConstants.VALID_SEAT_IDS) {
            seatState.put(seatId, SeatView.available(seatId));
        }
    }

    public void setBroadcaster(Consumer<String> broadcaster) {
        this.broadcaster = broadcaster;
    }

    public Consumer<String> getBroadcaster() {
        return broadcaster;
    }

    @Override
    public void dispatch(RequestContext context, String jsonLine) {
        if (context == null || jsonLine == null || jsonLine.isBlank()) {
            return;
        }

        JsonNode root;
        try {
            root = ProtocolJson.parseTree(jsonLine);
        } catch (Exception e) {
            ErrorReply errorReply = new ErrorReply(
                "N/A",
                ErrorCode.INVALID_REQUEST_FORMAT,
                "Malformed JSON frame: " + e.getMessage()
            );
            context.send(ProtocolJson.serialize(errorReply));
            return;
        }

        String typeStr = root.path("type").asText(null);
        String requestId = root.path("requestId").asText("N/A");

        if (typeStr == null) {
            ErrorReply errorReply = new ErrorReply(
                requestId,
                ErrorCode.INVALID_REQUEST_FORMAT,
                "Missing 'type' header in protocol frame"
            );
            context.send(ProtocolJson.serialize(errorReply));
            return;
        }

        MessageType type;
        try {
            type = MessageType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            ErrorReply errorReply = new ErrorReply(
                requestId,
                ErrorCode.UNKNOWN_REQUEST_TYPE,
                "Unknown request type: " + typeStr
            );
            context.send(ProtocolJson.serialize(errorReply));
            return;
        }

        switch (type) {
            case LOGIN -> {
                String username = root.path("username").asText("guest").trim();
                String userId = "USR-" + Math.abs(username.hashCode() % 1000);
                context.setAuthenticatedUserId(userId);
                LoginOkReply reply = new LoginOkReply(requestId, userId, username);
                context.send(ProtocolJson.serialize(reply));
            }
            case GET_SEATS -> {
                String showId = root.path("showId").asText(ProtocolConstants.DEFAULT_SHOW_ID);
                List<SeatView> seats;
                synchronized (seatState) {
                    seats = new ArrayList<>(seatState.values());
                }
                SeatSnapshotReply reply = new SeatSnapshotReply(requestId, showId, version.get(), seats);
                context.send(ProtocolJson.serialize(reply));
            }
            case BOOK_SEAT -> {
                if (!context.isAuthenticated()) {
                    ErrorReply error = new ErrorReply(
                        requestId,
                        ErrorCode.AUTH_REQUIRED,
                        "User must authenticate before booking seats"
                    );
                    context.send(ProtocolJson.serialize(error));
                    return;
                }
                String showId = root.path("showId").asText(ProtocolConstants.DEFAULT_SHOW_ID);
                String seatId = root.path("seatId").asText("A1").trim().toUpperCase();

                if (!ProtocolConstants.isValidSeatId(seatId)) {
                    ErrorReply error = new ErrorReply(
                        requestId,
                        ErrorCode.SEAT_NOT_FOUND,
                        "Seat ID " + seatId + " is invalid. Valid seats are A1-E8."
                    );
                    context.send(ProtocolJson.serialize(error));
                    return;
                }

                String userId = context.getAuthenticatedUserId().orElse("USR-anon");

                synchronized (seatState) {
                    SeatView current = seatState.get(seatId);
                    if (current != null && current.status() == SeatStatus.BOOKED) {
                        BookRejectedReply rejected = new BookRejectedReply(
                            requestId,
                            ErrorCode.SEAT_ALREADY_BOOKED,
                            "Seat " + seatId + " is already reserved by another user."
                        );
                        context.send(ProtocolJson.serialize(rejected));
                        return;
                    }

                    SeatView updated = new SeatView(seatId, SeatStatus.BOOKED, userId);
                    seatState.put(seatId, updated);
                    String bookingId = "BKG-" + System.currentTimeMillis();
                    bookingToSeat.put(bookingId, seatId);
                    seatToBooking.put(seatId, bookingId);
                    bookingOwners.put(bookingId, userId);

                    BookOkReply reply = new BookOkReply(
                        requestId,
                        bookingId,
                        showId,
                        seatId,
                        "Seat " + seatId + " booked successfully."
                    );
                    context.send(ProtocolJson.serialize(reply));

                    // Broadcast real-time seat update to all connected clients
                    if (broadcaster != null) {
                        SeatUpdatedEvent event = new SeatUpdatedEvent(
                            showId,
                            version.incrementAndGet(),
                            List.of(updated)
                        );
                        broadcaster.accept(ProtocolJson.serialize(event));
                    }
                }
            }
            case CANCEL_BOOKING -> {
                if (!context.isAuthenticated()) {
                    ErrorReply error = new ErrorReply(
                        requestId,
                        ErrorCode.AUTH_REQUIRED,
                        "User must authenticate before cancelling reservations"
                    );
                    context.send(ProtocolJson.serialize(error));
                    return;
                }

                String bookingId = root.path("bookingId").asText("").trim();
                String userId = context.getAuthenticatedUserId().orElse("USR-anon");

                synchronized (seatState) {
                    String seatId = bookingToSeat.get(bookingId);
                    if (seatId == null && ProtocolConstants.isValidSeatId(bookingId)) {
                        seatId = bookingId;
                        bookingId = seatToBooking.get(seatId);
                    }

                    if (seatId == null) {
                        CancelRejectedReply rejected = new CancelRejectedReply(
                            requestId,
                            ErrorCode.BOOKING_NOT_FOUND,
                            "No active booking found for identifier: " + bookingId
                        );
                        context.send(ProtocolJson.serialize(rejected));
                        return;
                    }

                    String owner = bookingOwners.get(bookingId);
                    if (owner != null && !owner.equals(userId)) {
                        CancelRejectedReply rejected = new CancelRejectedReply(
                            requestId,
                            ErrorCode.NOT_BOOKING_OWNER,
                            "User " + userId + " does not own booking " + bookingId
                        );
                        context.send(ProtocolJson.serialize(rejected));
                        return;
                    }

                    SeatView available = SeatView.available(seatId);
                    seatState.put(seatId, available);
                    if (bookingId != null) {
                        bookingToSeat.remove(bookingId);
                        bookingOwners.remove(bookingId);
                    }
                    seatToBooking.remove(seatId);

                    CancelOkReply reply = new CancelOkReply(
                        requestId,
                        bookingId != null ? bookingId : "BKG-0",
                        seatId,
                        "Booking cancelled successfully."
                    );
                    context.send(ProtocolJson.serialize(reply));

                    // Broadcast real-time seat update to all connected clients
                    if (broadcaster != null) {
                        SeatUpdatedEvent event = new SeatUpdatedEvent(
                            ProtocolConstants.DEFAULT_SHOW_ID,
                            version.incrementAndGet(),
                            List.of(available)
                        );
                        broadcaster.accept(ProtocolJson.serialize(event));
                    }
                }
            }
            case LOGOUT -> {
                context.clearAuthentication();
                LogoutOkReply reply = new LogoutOkReply(requestId, "Session terminated");
                context.send(ProtocolJson.serialize(reply));
            }
            default -> {
                ErrorReply error = new ErrorReply(
                    requestId,
                    ErrorCode.UNKNOWN_REQUEST_TYPE,
                    "Unhandled request type in mock: " + type
                );
                context.send(ProtocolJson.serialize(error));
            }
        }
    }
}
