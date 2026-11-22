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

public final class BookingRequestProcessor implements RequestDispatcher {
    private static final Logger LOG = Logger.getLogger(BookingRequestProcessor.class.getName());
    private static final ObjectReader FRAME = ProtocolJson.mapper().readerFor(JsonNode.class)
        .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
    private final AuthService auth;
    private final ReservationService reservations;

    public BookingRequestProcessor(AuthService auth, ReservationService reservations) {
        this.auth = auth;
        this.reservations = reservations;
    }

    @Override
    public void dispatch(RequestContext context, String line) {
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
        if (user.isEmpty()) {
            return new ErrorReply(id, ErrorCode.AUTH_REQUIRED, "Login required");
        }
        return switch (type) {
            case "GET_SHOWS", "GET_SHOW_DETAILS" -> {
                if (!(reservations instanceof CatalogService catalog)) {
                    yield new ErrorReply(id, ErrorCode.UNKNOWN_REQUEST_TYPE, "Catalog unavailable");
                }
                if (type.equals("GET_SHOWS")) {
                    yield new ShowListReply(id, catalog.getShows());
                }
                yield catalog.getShowDetails(required(frame, "showId"))
                    .<ReplyMessage>map(show -> new ShowDetailsReply(id, show))
                    .orElseGet(() -> new ErrorReply(id, ErrorCode.SHOW_NOT_FOUND, "Unknown show"));
            }
            case "GET_SEATS" -> {
                String showId = required(frame, "showId");
                if (reservations instanceof CatalogService catalog && catalog.getShowDetails(showId).isEmpty()) {
                    yield new ErrorReply(id, ErrorCode.SHOW_NOT_FOUND, "Unknown show");
                }
                var result = reservations.snapshot(showId);
                yield new SeatSnapshotReply(id, result.showId(), result.version(), result.seats());
            }
            case "HOLD_SEAT", "RELEASE_HOLD", "CONFIRM_HOLD" -> holdReply(type, frame, id, user.get());
            case "JOIN_WAITLIST", "LEAVE_WAITLIST", "GET_WAITLIST_STATUS" ->
                waitlistReply(type, id, user.get(), required(frame, "showId"));
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
            default -> new ErrorReply(id, ErrorCode.UNKNOWN_REQUEST_TYPE, "Unknown request type");
        };
    }

    private ReplyMessage waitlistReply(String type, String id, String user, String showId) {
        if (!(reservations instanceof WaitlistService service)) {
            return new ErrorReply(id, ErrorCode.UNKNOWN_REQUEST_TYPE, "Waitlist unavailable");
        }
        if (type.equals("JOIN_WAITLIST")) {
            var result = service.joinWaitlist(user, showId);
            return result.success()
                ? new WaitlistJoinedOkReply(id, result.waitlistId(), showId, result.position(), result.message())
                : new WaitlistJoinedRejectedReply(id, showId, result.errorCode(), result.message());
        }
        if (type.equals("LEAVE_WAITLIST")) {
            var result = service.leaveWaitlist(user, showId);
            return result.success() ? new WaitlistLeftOkReply(id, showId, result.message())
                : new ErrorReply(id, result.errorCode(), result.message());
        }
        return service.status(user, showId).<ReplyMessage>map(status ->
            new WaitlistStatusReply(id, showId, status.position(), status.totalWaitlistSize()))
            .orElseGet(() -> new ErrorReply(id, ErrorCode.SHOW_NOT_FOUND, "Unknown show"));
    }

    private ReplyMessage holdReply(String type, JsonNode frame, String id, String user) {
        if (!(reservations instanceof HoldService service)) {
            return new ErrorReply(id, ErrorCode.UNKNOWN_REQUEST_TYPE, "Holds unavailable");
        }
        if (type.equals("HOLD_SEAT")) {
            var duration = frame.get("holdDurationSeconds");
            if (duration != null && (!duration.isIntegralNumber() || !duration.canConvertToInt())) {
                throw new InvalidFrame();
            }
            long seconds = duration == null ? 120 : duration.intValue();
            var result = service.holdSeat(user, required(frame, "showId"), required(frame, "seatId"), seconds * 1000);
            return result.success()
                ? new HoldOkReply(id, result.holdId(), result.showId(), result.seatId(), result.expiresAt(), result.message())
                : new HoldRejectedReply(id, result.showId(), result.seatId(), result.errorCode(), result.message());
        }
        String holdId = required(frame, "holdId");
        if (type.equals("RELEASE_HOLD")) {
            var result = service.releaseHold(user, holdId);
            return result.success()
                ? new HoldReleasedOkReply(id, holdId, result.showId(), result.seatId(), result.message())
                : new HoldReleasedRejectedReply(id, holdId, result.errorCode(), result.message());
        }
        var booking = service.confirmHold(user, holdId, required(frame, "idempotencyKey"));
        // Reserve a stable ticket reference for the subsequent ticket issuance stage.
        return booking.success()
            ? new ConfirmOkReply(id, booking.bookingId(), booking.bookingId().replaceFirst("^BKG-", "TCK-"),
                booking.showId(), booking.seatId(), booking.message())
            : new ConfirmRejectedReply(id, holdId, booking.errorCode(), booking.message());
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
