package com.seatsync.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import com.seatsync.common.contracts.RequestContext;
import com.seatsync.common.contracts.RequestDispatcher;
import com.seatsync.common.model.ErrorCode;
import com.seatsync.common.model.ProtocolConstants;
import com.seatsync.common.protocol.ProtocolJson;
import com.seatsync.common.protocol.reply.ErrorReply;
import com.seatsync.common.protocol.reply.LoginFailedReply;
import com.seatsync.common.protocol.reply.LoginOkReply;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

/** Authentication and strict request validation in front of a booking dispatcher. */
public final class SecurityRequestDispatcher implements RequestDispatcher {
    private static final Logger LOG = Logger.getLogger(SecurityRequestDispatcher.class.getName());
    private static final ObjectReader READER = ProtocolJson.mapper().readerFor(JsonNode.class)
        .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
    private final com.seatsync.common.contracts.AuthService auth;
    private final AuthorizationService authorization;
    private final RequestDispatcher booking;
    private final SecurityAuditLog audit;

    public SecurityRequestDispatcher(com.seatsync.common.contracts.AuthService auth,
            AuthorizationService authorization, RequestDispatcher booking) {
        this(auth, authorization, booking, new SecurityAuditLog());
    }

    public SecurityRequestDispatcher(com.seatsync.common.contracts.AuthService auth,
            AuthorizationService authorization, RequestDispatcher booking, SecurityAuditLog audit) {
        this.auth = Objects.requireNonNull(auth);
        this.authorization = Objects.requireNonNull(authorization);
        this.booking = Objects.requireNonNull(booking);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public void dispatch(RequestContext context, String jsonLine) {
        String id = "N/A";
        try {
            if (jsonLine == null || jsonLine.getBytes(ProtocolConstants.CHARSET).length > ProtocolConstants.MAX_FRAME_BYTES) {
                throw new InvalidRequest();
            }
            JsonNode frame = READER.readValue(jsonLine);
            if (frame == null || !frame.isObject()) {
                throw new InvalidRequest();
            }
            String candidateId = text(frame, "requestId");
            if (!candidateId.matches("[A-Za-z0-9._-]{1,128}")) {
                throw new InvalidRequest();
            }
            id = candidateId;
            String type = text(frame, "type");
            Set<String> allowed = switch (type) {
                case "LOGIN" -> Set.of("type", "requestId", "username", "password");
                case "GET_SHOWS" -> Set.of("type", "requestId");
                case "GET_SEATS", "GET_SHOW_DETAILS", "JOIN_WAITLIST", "LEAVE_WAITLIST", "GET_WAITLIST_STATUS" -> Set.of("type", "requestId", "showId");
                case "HOLD_SEAT" -> Set.of("type", "requestId", "showId", "seatId", "holdDurationSeconds");
                case "HOLD_SEATS" -> Set.of("type", "requestId", "showId", "seatIds", "holdDurationSeconds");
                case "RELEASE_HOLD" -> Set.of("type", "requestId", "holdId");
                case "CONFIRM_HOLD" -> Set.of("type", "requestId", "holdId", "idempotencyKey");
                case "BOOK_SEAT" -> Set.of("type", "requestId", "showId", "seatId");
                case "CANCEL_BOOKING" -> Set.of("type", "requestId", "bookingId");
                case "LOGOUT" -> Set.of("type", "requestId");
                case "ADMIN_ADD_SHOW" -> Set.of("type", "requestId", "showDetails");
                case "ADMIN_CREATE_MOVIE" -> Set.of("type", "requestId", "movie");
                case "ADMIN_CREATE_SHOW" -> Set.of("type", "requestId", "movieId", "showDetails");
                case "ADMIN_DISABLE_SHOW" -> Set.of("type", "requestId", "showId");
                case "ADMIN_LIST_BOOKINGS" -> Set.of("type", "requestId", "showId", "pageNumber", "pageSize");
                case "GET_MOVIES" -> Set.of("type", "requestId", "pageNumber", "pageSize");
                case "ADMIN_UPDATE_SEAT_STATUS" -> Set.of("type", "requestId", "showId", "seatId", "status");
                case "ADMIN_CANCEL_HOLD" -> Set.of("type", "requestId", "holdId", "reason");
                case "ADMIN_GET_AUDIT_LOGS" -> Set.of("type", "requestId", "limit");
                case "GET_NETWORK_METRICS", "GET_SERVER_METRICS" -> Set.of("type", "requestId");
                case "SUBSCRIBE_EVENTS", "UNSUBSCRIBE_EVENTS" ->
                    Set.of("type", "requestId", "showId");
                case "GET_TICKET" -> Set.of("type", "requestId", "ticketId");
                case "VERIFY_TICKET" -> Set.of("type", "requestId", "ticketId", "verificationHash");
                case "GET_BOOKING_HISTORY" -> Set.of("type", "requestId", "pageNumber", "pageSize");
                default -> null;
            };
            if (allowed == null) {
                sendError(context, id, ErrorCode.UNKNOWN_REQUEST_TYPE, "Unknown request type");
                return;
            }
            var fields = frame.fieldNames();
            while (fields.hasNext()) {
                if (!allowed.contains(fields.next())) {
                    throw new InvalidRequest();
                }
            }
            if ("LOGIN".equals(type)) {
                if (authorization.isAuthenticated(context)) {
                    context.send(ProtocolJson.serialize(new LoginFailedReply(id, ErrorCode.ALREADY_AUTHENTICATED,
                        "Already logged in")));
                    return;
                }
                String username = text(frame, "username");
                char[] password = text(frame, "password").toCharArray();
                try {
                    var result = auth.login(username, password);
                    if (result.success()) {
                        context.setAuthenticatedUserId(result.userId());
                        audit.record(result.userId(), "LOGIN", id, SecurityAuditLog.Outcome.SUCCESS);
                        context.send(ProtocolJson.serialize(new LoginOkReply(id, result.userId(), result.username(),
                            authorization.roleOf(context).orElse(com.seatsync.common.model.UserRole.CUSTOMER))));
                    } else {
                        audit.record("anonymous", "LOGIN", id, SecurityAuditLog.Outcome.DENIED);
                        context.send(ProtocolJson.serialize(new LoginFailedReply(id, ErrorCode.INVALID_CREDENTIALS,
                            "Username or password incorrect")));
                    }
                } finally {
                    Arrays.fill(password, '\0');
                }
                return;
            }
            if (!authorization.isAuthenticated(context)) {
                audit.record("anonymous", "AUTHORIZATION", id, SecurityAuditLog.Outcome.DENIED);
                sendError(context, id, ErrorCode.AUTH_REQUIRED, "Login required");
                return;
            }
            if ((type.startsWith("ADMIN_") || type.equals("GET_NETWORK_METRICS") || type.equals("GET_SERVER_METRICS"))
                    && !authorization.isAdmin(context.getAuthenticatedUserId().orElse(null))) {
                audit.record(context.getAuthenticatedUserId().orElse("anonymous"), type, id, SecurityAuditLog.Outcome.DENIED);
                sendError(context, id, ErrorCode.FORBIDDEN_ROLE, "Administrator permission required");
                return;
            }
            if ("GET_SEATS".equals(type) || "GET_SHOW_DETAILS".equals(type) || "BOOK_SEAT".equals(type)) {
                if (text(frame, "showId").isBlank()) {
                    throw new InvalidRequest();
                }
            }
            if ("BOOK_SEAT".equals(type) && !text(frame, "seatId").matches("[A-Z][1-9][0-9]*")) {
                sendError(context, id, ErrorCode.SEAT_NOT_FOUND, "Invalid seat ID");
                return;
            }
            if ("CANCEL_BOOKING".equals(type) && !InputValidator.bookingId(text(frame, "bookingId"))) {
                throw new InvalidRequest();
            }
            booking.dispatch(context, jsonLine);
        } catch (JsonProcessingException | InvalidRequest e) {
            audit.record(context.getAuthenticatedUserId().orElse("anonymous"), "REQUEST_VALIDATION", id,
                SecurityAuditLog.Outcome.INVALID);
            sendError(context, id, ErrorCode.INVALID_REQUEST_FORMAT, "Invalid request fields or JSON");
        } catch (RuntimeException e) {
            LOG.severe("Security request processing failed");
            sendError(context, id, ErrorCode.INTERNAL_ERROR, "Request processing failed");
        }
    }

    private static String text(JsonNode frame, String name) {
        JsonNode node = frame.get(name);
        if (node == null || !node.isTextual()) {
            throw new InvalidRequest();
        }
        return node.textValue();
    }

    private static void sendError(RequestContext context, String id, ErrorCode code, String message) {
        context.send(ProtocolJson.serialize(new ErrorReply(id, code, message)));
    }

    private static final class InvalidRequest extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
