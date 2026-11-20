package com.seatsync.concurrency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.seatsync.common.contracts.*;
import com.seatsync.common.model.*;
import com.seatsync.common.protocol.*;
import com.seatsync.common.protocol.reply.*;
import java.util.Arrays;
import java.util.logging.Logger;

final class BookingRequestProcessor {
    private static final Logger LOG = Logger.getLogger(BookingRequestProcessor.class.getName());
    private static final ObjectReader FRAME = ProtocolJson.mapper().readerFor(JsonNode.class)
        .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
    private final AuthService auth;
    private final ReservationService reservations;

    BookingRequestProcessor(AuthService auth, ReservationService reservations) {
        this.auth = auth;
        this.reservations = reservations;
    }

    void process(RequestContext context, String line) {
        String id = "N/A";
        ReplyMessage reply;
        try {
            JsonNode frame = FRAME.readValue(line);
            id = required(frame, "requestId");
            String type = required(frame, "type");
            if (frame.has("userId")) {
                throw new InvalidFrame();
            }
            reply = handle(context, frame, id, type);
        } catch (JsonProcessingException | InvalidFrame failure) {
            reply = new ErrorReply(id, ErrorCode.INVALID_REQUEST_FORMAT, "Invalid request fields or JSON");
        } catch (RuntimeException failure) {
            reply = new ErrorReply(id, ErrorCode.INTERNAL_ERROR, "Request processing failed");
        }
        LOG.info("worker=" + Thread.currentThread().getName() + " outcome=" + reply.getType());
        context.send(ProtocolJson.serialize(reply));
    }

    private ReplyMessage handle(RequestContext context, JsonNode frame, String id, String type) {
        var user = context.getAuthenticatedUserId();
        if (type.equals("LOGIN")) {
            if (user.isPresent()) {
                return new LoginFailedReply(id, ErrorCode.ALREADY_AUTHENTICATED, "Already logged in");
            }
            String username = required(frame, "username");
            char[] password = required(frame, "password").toCharArray();
            try {
                var result = auth.login(username, password);
                if (!result.success()) {
                    return new LoginFailedReply(id, result.errorCode(), "Login failed");
                }
                context.setAuthenticatedUserId(result.userId());
                return new LoginOkReply(id, result.userId(), result.username());
            } finally {
                Arrays.fill(password, '\0');
            }
        }
        if (!type.equals("GET_SEATS") && !type.equals("BOOK_SEAT") &&
            !type.equals("CANCEL_BOOKING") && !type.equals("LOGOUT")) {
            return new ErrorReply(id, ErrorCode.UNKNOWN_REQUEST_TYPE, "Unknown request type");
        }
        if (user.isEmpty()) {
            return new ErrorReply(id, ErrorCode.AUTH_REQUIRED, "Login required");
        }
        return switch (type) {
            case "GET_SEATS" -> {
                var result = reservations.snapshot(required(frame, "showId"));
                yield new SeatSnapshotReply(id, result.showId(), result.version(), result.seats());
            }
            case "BOOK_SEAT" -> {
                var result = reservations.book(user.get(), required(frame, "showId"), required(frame, "seatId"));
                yield result.success()
                    ? new BookOkReply(id, result.bookingId(), result.showId(), result.seatId(), result.message())
                    : new BookRejectedReply(id, result.errorCode(), result.message());
            }
            case "CANCEL_BOOKING" -> {
                var result = reservations.cancel(user.get(), required(frame, "bookingId"));
                yield result.success()
                    ? new CancelOkReply(id, result.bookingId(), result.seatId(), result.message())
                    : new CancelRejectedReply(id, result.errorCode(), result.message());
            }
            case "LOGOUT" -> {
                context.clearAuthentication();
                yield new LogoutOkReply(id, "Logged out");
            }
            default -> throw new IllegalStateException();
        };
    }

    private static String required(JsonNode frame, String name) {
        JsonNode value = frame == null ? null : frame.get(name);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new InvalidFrame();
        }
        return value.textValue();
    }

    private static final class InvalidFrame extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
