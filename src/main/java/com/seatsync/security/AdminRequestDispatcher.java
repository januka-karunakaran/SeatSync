package com.seatsync.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.seatsync.common.contracts.AdminManagementService;
import com.seatsync.common.contracts.RequestContext;
import com.seatsync.common.contracts.RequestDispatcher;
import com.seatsync.common.model.*;
import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.ProtocolJson;
import com.seatsync.common.protocol.ReplyMessage;
import com.seatsync.common.protocol.reply.*;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/** Runs on workers behind SecurityRequestDispatcher. Rechecks admin policy at the command boundary. */
public final class AdminRequestDispatcher implements RequestDispatcher {
    private final AuthorizationService authorization;
    private final AdminManagementService admin;
    private final SecurityAuditLog audit;
    private final Supplier<NetworkMetricsSnapshot> networkMetrics;
    private final Supplier<ServerMetricSnapshot> serverMetrics;
    private final RequestDispatcher next;

    public AdminRequestDispatcher(AuthorizationService authorization, AdminManagementService admin,
            SecurityAuditLog audit, Supplier<NetworkMetricsSnapshot> networkMetrics,
            Supplier<ServerMetricSnapshot> serverMetrics, RequestDispatcher next) {
        this.authorization = Objects.requireNonNull(authorization);
        this.admin = Objects.requireNonNull(admin);
        this.audit = Objects.requireNonNull(audit);
        this.networkMetrics = Objects.requireNonNull(networkMetrics);
        this.serverMetrics = Objects.requireNonNull(serverMetrics);
        this.next = Objects.requireNonNull(next);
    }

    @Override public void dispatch(RequestContext context, String line) {
        String id = "N-A";
        String action = "ADMIN_UNKNOWN";
        String actor = context.getAuthenticatedUserId().orElse("anonymous");
        SecurityAuditLog.Outcome outcome;
        ReplyMessage reply;
        try {
            JsonNode frame = ProtocolJson.mapper().readerFor(JsonNode.class)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
                .readValue(line);
            id = text(frame, "requestId");
            String type = text(frame, "type");
            if (!type.startsWith("ADMIN_") && !type.equals("GET_MOVIES") && !type.equals("GET_NETWORK_METRICS") && !type.equals("GET_SERVER_METRICS")) {
                next.dispatch(context, line);
                return;
            }
            try { action = MessageType.valueOf(type).name(); }
            catch (IllegalArgumentException ignored) { /* Unknown action stays a fixed label. */ }
            if (!context.isAuthenticated()) {
                reply = new ErrorReply(id, ErrorCode.AUTH_REQUIRED, "Login required");
                outcome = SecurityAuditLog.Outcome.DENIED;
            } else if (!type.equals("GET_MOVIES") && !authorization.isAdmin(actor)) {
                reply = new ErrorReply(id, ErrorCode.FORBIDDEN_ROLE, "Administrator permission required");
                outcome = SecurityAuditLog.Outcome.DENIED;
            } else {
                if (frame.has("userId") || frame.has("actor") || frame.has("role")) {
                    throw new IllegalArgumentException("Forged identity");
                }
                reply = handle(actor, id, type, frame);
                outcome = reply instanceof ErrorReply ? SecurityAuditLog.Outcome.FAILURE : SecurityAuditLog.Outcome.SUCCESS;
            }
        } catch (AdminOperationException rejected) {
            reply = new ErrorReply(id, rejected.errorCode(), rejected.getMessage());
            outcome = SecurityAuditLog.Outcome.FAILURE;
        } catch (SecurityException denied) {
            reply = new ErrorReply(id, ErrorCode.FORBIDDEN_ROLE, "Administrator permission required");
            outcome = SecurityAuditLog.Outcome.DENIED;
        } catch (UnsupportedOperationException unavailable) {
            reply = new ErrorReply(id, ErrorCode.UNKNOWN_REQUEST_TYPE, "Admin operation is not installed");
            outcome = SecurityAuditLog.Outcome.UNAVAILABLE;
        } catch (JsonProcessingException | IllegalArgumentException invalid) {
            reply = new ErrorReply(id, ErrorCode.INVALID_REQUEST_FORMAT, "Invalid administrative request");
            outcome = SecurityAuditLog.Outcome.INVALID;
        } catch (RuntimeException failure) {
            reply = new ErrorReply(id, ErrorCode.INTERNAL_ERROR, "Administrative request failed");
            outcome = SecurityAuditLog.Outcome.FAILURE;
        }
        audit.record(actor, action, id, outcome);
        context.send(ProtocolJson.serialize(reply));
    }

    private ReplyMessage handle(String actor, String id, String type, JsonNode frame) throws JsonProcessingException {
        return switch (type) {
            case "ADMIN_CREATE_MOVIE" -> {
                fields(frame, "movie");
                if (!frame.path("movie").isObject()) { throw new IllegalArgumentException("Missing movie"); }
                MovieDetails movie = ProtocolJson.mapper().readerFor(MovieDetails.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(frame.get("movie").toString());
                yield extended().createMovie(actor, movie)
                    ? new AdminMovieCreatedReply(id, movie.movieId(), "Movie created")
                    : new ErrorReply(id, ErrorCode.MOVIE_ALREADY_EXISTS, "Movie already exists");
            }
            case "ADMIN_CREATE_SHOW" -> {
                fields(frame, "movieId", "showDetails");
                if (!frame.path("showDetails").isObject()) { throw new IllegalArgumentException("Missing show"); }
                ShowDetails show = ProtocolJson.mapper().readerFor(ShowDetails.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(frame.get("showDetails").toString());
                yield extended().createShow(actor, text(frame, "movieId"), show)
                    ? new AdminShowAddedReply(id, show.showId(), "Show created")
                    : new ErrorReply(id, ErrorCode.SHOW_ALREADY_EXISTS, "Show already exists");
            }
            case "ADMIN_DISABLE_SHOW" -> {
                fields(frame, "showId");
                String showId = text(frame, "showId");
                yield extended().disableShow(actor, showId)
                    ? new AdminShowDisabledReply(id, showId, "Show disabled; confirmed bookings retained")
                    : new ErrorReply(id, ErrorCode.INVALID_STATE, "Show already disabled");
            }
            case "ADMIN_LIST_BOOKINGS" -> {
                fields(frame, "showId", "pageNumber", "pageSize");
                yield new AdminBookingsReply(id, extended().listBookings(actor, text(frame, "showId"),
                    integer(frame, "pageNumber"), integer(frame, "pageSize")));
            }
            case "GET_MOVIES" -> {
                fields(frame, "pageNumber", "pageSize");
                yield new MovieListReply(id, extended().movies(integer(frame, "pageNumber"), integer(frame, "pageSize")));
            }
            case "ADMIN_ADD_SHOW" -> {
                fields(frame, "showDetails");
                if (!frame.path("showDetails").isObject()) { throw new IllegalArgumentException("Missing show"); }
                ShowDetails show = ProtocolJson.mapper().readerFor(ShowDetails.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(frame.get("showDetails").toString());
                yield admin.addShow(actor, show)
                    ? new AdminShowAddedReply(id, show.showId(), "Show added")
                    : new ErrorReply(id, ErrorCode.SHOW_ALREADY_EXISTS, "Show already exists");
            }
            case "ADMIN_UPDATE_SEAT_STATUS" -> {
                fields(frame, "showId", "seatId", "status");
                String show = text(frame, "showId"), seat = text(frame, "seatId");
                SeatStatus status = SeatStatus.valueOf(text(frame, "status"));
                yield admin.updateSeatStatus(actor, show, seat, status)
                    ? new AdminSeatUpdatedReply(id, show, seat, status, "Seat updated")
                    : new ErrorReply(id, ErrorCode.SEAT_NOT_FOUND, "Seat could not be updated");
            }
            case "ADMIN_CANCEL_HOLD" -> {
                fields(frame, "holdId", "reason");
                String hold = text(frame, "holdId");
                yield admin.cancelHold(actor, hold, text(frame, "reason"))
                    ? new AdminHoldCancelledReply(id, hold, "Hold cancelled")
                    : new ErrorReply(id, ErrorCode.HOLD_NOT_FOUND, "Hold not found");
            }
            case "ADMIN_GET_AUDIT_LOGS" -> {
                fields(frame, "limit");
                JsonNode limit = frame.get("limit");
                if (limit == null || !limit.isIntegralNumber() || !limit.canConvertToInt()) {
                    throw new IllegalArgumentException("Invalid limit");
                }
                yield new AdminAuditLogsReply(id, admin.getAuditLogs(actor, limit.intValue()));
            }
            case "GET_NETWORK_METRICS" -> {
                fields(frame);
                yield new NetworkMetricsReply(id, networkMetrics.get());
            }
            case "GET_SERVER_METRICS" -> {
                fields(frame);
                yield new ServerMetricsReply(id, serverMetrics.get());
            }
            default -> new ErrorReply(id, ErrorCode.UNKNOWN_REQUEST_TYPE, "Unknown admin operation");
        };
    }

    private static void fields(JsonNode frame, String... payload) {
        var allowed = new java.util.HashSet<>(Set.of("requestId", "type"));
        allowed.addAll(java.util.List.of(payload));
        frame.fieldNames().forEachRemaining(name -> {
            if (!allowed.contains(name)) { throw new IllegalArgumentException("Unexpected field"); }
        });
    }
    private AdminService extended() {
        if (admin instanceof AdminService service) { return service; }
        throw new UnsupportedOperationException("Extended admin service is not installed");
    }
    private static int integer(JsonNode frame, String field) {
        JsonNode value = frame.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) { throw new IllegalArgumentException("Invalid integer"); }
        return value.intValue();
    }
    private static String text(JsonNode frame, String field) {
        JsonNode value = frame == null ? null : frame.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException("Missing field");
        }
        return value.textValue();
    }
}
