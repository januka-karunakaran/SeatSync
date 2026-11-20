package com.seatsync.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.seatsync.common.contracts.RequestContext;
import com.seatsync.common.contracts.RequestDispatcher;
import com.seatsync.common.model.ErrorCode;
import com.seatsync.common.model.ProtocolConstants;
import com.seatsync.common.model.SeatSnapshot;
import com.seatsync.common.model.SeatStatus;
import com.seatsync.common.model.SeatView;
import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.ProtocolJson;
import com.seatsync.common.protocol.reply.BookOkReply;
import com.seatsync.common.protocol.reply.CancelOkReply;
import com.seatsync.common.protocol.reply.ErrorReply;
import com.seatsync.common.protocol.reply.LoginOkReply;
import com.seatsync.common.protocol.reply.LogoutOkReply;
import com.seatsync.common.protocol.reply.SeatSnapshotReply;

import java.util.ArrayList;
import java.util.List;

/**
 * Temporary mock implementation of {@link RequestDispatcher} for Stage A.
 * Allows NioBookingServer to run independently, parse wire messages, and reply
 * with valid protocol responses before Member 03 concurrency/booking modules merge.
 */
public class MockRequestDispatcher implements RequestDispatcher {

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
                String username = root.path("username").asText("guest");
                String userId = "USR-" + Math.abs(username.hashCode() % 1000);
                context.setAuthenticatedUserId(userId);
                LoginOkReply reply = new LoginOkReply(requestId, userId, username);
                context.send(ProtocolJson.serialize(reply));
            }
            case GET_SEATS -> {
                String showId = root.path("showId").asText(ProtocolConstants.DEFAULT_SHOW_ID);
                List<SeatView> seats = new ArrayList<>(40);
                for (String seatId : ProtocolConstants.VALID_SEAT_IDS) {
                    seats.add(new SeatView(seatId, SeatStatus.AVAILABLE, null));
                }
                SeatSnapshotReply reply = new SeatSnapshotReply(requestId, showId, 1L, seats);
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
                String seatId = root.path("seatId").asText("A1");
                String bookingId = "BKG-" + System.currentTimeMillis();
                BookOkReply reply = new BookOkReply(
                    requestId,
                    bookingId,
                    showId,
                    seatId,
                    "Mock booking confirmed for seat " + seatId
                );
                context.send(ProtocolJson.serialize(reply));
            }
            case CANCEL_BOOKING -> {
                String bookingId = root.path("bookingId").asText("BKG-000");
                CancelOkReply reply = new CancelOkReply(
                    requestId,
                    bookingId,
                    "A1",
                    "Mock cancellation confirmed"
                );
                context.send(ProtocolJson.serialize(reply));
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
