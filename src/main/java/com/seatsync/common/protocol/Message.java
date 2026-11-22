package com.seatsync.common.protocol;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.seatsync.common.protocol.event.MetricsUpdatedEvent;
import com.seatsync.common.protocol.event.SeatHeldEvent;
import com.seatsync.common.protocol.event.SeatReleasedEvent;
import com.seatsync.common.protocol.event.SeatUpdatedEvent;
import com.seatsync.common.protocol.event.ShowUpdatedEvent;
import com.seatsync.common.protocol.event.WaitlistPromotedEvent;
import com.seatsync.common.protocol.reply.AdminAuditLogsReply;
import com.seatsync.common.protocol.reply.AdminHoldCancelledReply;
import com.seatsync.common.protocol.reply.AdminSeatUpdatedReply;
import com.seatsync.common.protocol.reply.AdminShowAddedReply;
import com.seatsync.common.protocol.reply.BookOkReply;
import com.seatsync.common.protocol.reply.BookRejectedReply;
import com.seatsync.common.protocol.reply.BookingHistoryReply;
import com.seatsync.common.protocol.reply.CancelOkReply;
import com.seatsync.common.protocol.reply.CancelRejectedReply;
import com.seatsync.common.protocol.reply.ConfirmOkReply;
import com.seatsync.common.protocol.reply.ConfirmRejectedReply;
import com.seatsync.common.protocol.reply.ErrorReply;
import com.seatsync.common.protocol.reply.HoldOkReply;
import com.seatsync.common.protocol.reply.HoldRejectedReply;
import com.seatsync.common.protocol.reply.HoldReleasedOkReply;
import com.seatsync.common.protocol.reply.HoldReleasedRejectedReply;
import com.seatsync.common.protocol.reply.LoginFailedReply;
import com.seatsync.common.protocol.reply.LoginOkReply;
import com.seatsync.common.protocol.reply.LogoutOkReply;
import com.seatsync.common.protocol.reply.SeatSnapshotReply;
import com.seatsync.common.protocol.reply.ServerMetricsReply;
import com.seatsync.common.protocol.reply.ShowDetailsReply;
import com.seatsync.common.protocol.reply.ShowListReply;
import com.seatsync.common.protocol.reply.SubscribeOkReply;
import com.seatsync.common.protocol.reply.TicketReply;
import com.seatsync.common.protocol.reply.UnsubscribeOkReply;
import com.seatsync.common.protocol.reply.WaitlistJoinedOkReply;
import com.seatsync.common.protocol.reply.WaitlistJoinedRejectedReply;
import com.seatsync.common.protocol.reply.WaitlistLeftOkReply;
import com.seatsync.common.protocol.reply.WaitlistStatusReply;
import com.seatsync.common.protocol.request.AdminAddShowRequest;
import com.seatsync.common.protocol.request.AdminCancelHoldRequest;
import com.seatsync.common.protocol.request.AdminGetAuditLogsRequest;
import com.seatsync.common.protocol.request.AdminUpdateSeatStatusRequest;
import com.seatsync.common.protocol.request.BookSeatRequest;
import com.seatsync.common.protocol.request.CancelBookingRequest;
import com.seatsync.common.protocol.request.ConfirmHoldRequest;
import com.seatsync.common.protocol.request.GetBookingHistoryRequest;
import com.seatsync.common.protocol.request.GetSeatsRequest;
import com.seatsync.common.protocol.request.GetServerMetricsRequest;
import com.seatsync.common.protocol.request.GetShowDetailsRequest;
import com.seatsync.common.protocol.request.GetShowsRequest;
import com.seatsync.common.protocol.request.GetTicketRequest;
import com.seatsync.common.protocol.request.GetWaitlistStatusRequest;
import com.seatsync.common.protocol.request.HoldSeatRequest;
import com.seatsync.common.protocol.request.JoinWaitlistRequest;
import com.seatsync.common.protocol.request.LeaveWaitlistRequest;
import com.seatsync.common.protocol.request.LoginRequest;
import com.seatsync.common.protocol.request.LogoutRequest;
import com.seatsync.common.protocol.request.ReleaseHoldRequest;
import com.seatsync.common.protocol.request.SubscribeEventsRequest;
import com.seatsync.common.protocol.request.UnsubscribeEventsRequest;

/**
 * Root interface for all protocol messages exchanged over the TCP wire.
 * Automatically handles Jackson polymorphic type discrimination via the "type" property.
 * Fully supports all v1 and SeatSync 2.0 messages.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
    visible = true
)
@JsonSubTypes({
    // V1 Requests
    @JsonSubTypes.Type(value = LoginRequest.class, name = "LOGIN"),
    @JsonSubTypes.Type(value = GetSeatsRequest.class, name = "GET_SEATS"),
    @JsonSubTypes.Type(value = BookSeatRequest.class, name = "BOOK_SEAT"),
    @JsonSubTypes.Type(value = CancelBookingRequest.class, name = "CANCEL_BOOKING"),
    @JsonSubTypes.Type(value = LogoutRequest.class, name = "LOGOUT"),

    // V2 Requests: Catalog
    @JsonSubTypes.Type(value = GetShowsRequest.class, name = "GET_SHOWS"),
    @JsonSubTypes.Type(value = GetShowDetailsRequest.class, name = "GET_SHOW_DETAILS"),

    // V2 Requests: Holds
    @JsonSubTypes.Type(value = HoldSeatRequest.class, name = "HOLD_SEAT"),
    @JsonSubTypes.Type(value = ReleaseHoldRequest.class, name = "RELEASE_HOLD"),
    @JsonSubTypes.Type(value = ConfirmHoldRequest.class, name = "CONFIRM_HOLD"),

    // V2 Requests: Waitlist
    @JsonSubTypes.Type(value = JoinWaitlistRequest.class, name = "JOIN_WAITLIST"),
    @JsonSubTypes.Type(value = LeaveWaitlistRequest.class, name = "LEAVE_WAITLIST"),
    @JsonSubTypes.Type(value = GetWaitlistStatusRequest.class, name = "GET_WAITLIST_STATUS"),

    // V2 Requests: Tickets & History
    @JsonSubTypes.Type(value = GetTicketRequest.class, name = "GET_TICKET"),
    @JsonSubTypes.Type(value = GetBookingHistoryRequest.class, name = "GET_BOOKING_HISTORY"),

    // V2 Requests: Admin
    @JsonSubTypes.Type(value = AdminAddShowRequest.class, name = "ADMIN_ADD_SHOW"),
    @JsonSubTypes.Type(value = AdminUpdateSeatStatusRequest.class, name = "ADMIN_UPDATE_SEAT_STATUS"),
    @JsonSubTypes.Type(value = AdminGetAuditLogsRequest.class, name = "ADMIN_GET_AUDIT_LOGS"),
    @JsonSubTypes.Type(value = AdminCancelHoldRequest.class, name = "ADMIN_CANCEL_HOLD"),

    // V2 Requests: Telemetry & Pub/Sub
    @JsonSubTypes.Type(value = GetServerMetricsRequest.class, name = "GET_SERVER_METRICS"),
    @JsonSubTypes.Type(value = SubscribeEventsRequest.class, name = "SUBSCRIBE_EVENTS"),
    @JsonSubTypes.Type(value = UnsubscribeEventsRequest.class, name = "UNSUBSCRIBE_EVENTS"),

    // V1 Replies
    @JsonSubTypes.Type(value = LoginOkReply.class, name = "LOGIN_OK"),
    @JsonSubTypes.Type(value = LoginFailedReply.class, name = "LOGIN_FAILED"),
    @JsonSubTypes.Type(value = SeatSnapshotReply.class, name = "SEAT_SNAPSHOT"),
    @JsonSubTypes.Type(value = BookOkReply.class, name = "BOOK_OK"),
    @JsonSubTypes.Type(value = BookRejectedReply.class, name = "BOOK_REJECTED"),
    @JsonSubTypes.Type(value = CancelOkReply.class, name = "CANCEL_OK"),
    @JsonSubTypes.Type(value = CancelRejectedReply.class, name = "CANCEL_REJECTED"),
    @JsonSubTypes.Type(value = LogoutOkReply.class, name = "LOGOUT_OK"),
    @JsonSubTypes.Type(value = ErrorReply.class, name = "ERROR"),

    // V2 Replies: Catalog
    @JsonSubTypes.Type(value = ShowListReply.class, name = "SHOW_LIST_REPLY"),
    @JsonSubTypes.Type(value = ShowDetailsReply.class, name = "SHOW_DETAILS_REPLY"),

    // V2 Replies: Holds
    @JsonSubTypes.Type(value = HoldOkReply.class, name = "HOLD_OK"),
    @JsonSubTypes.Type(value = HoldRejectedReply.class, name = "HOLD_REJECTED"),
    @JsonSubTypes.Type(value = HoldReleasedOkReply.class, name = "HOLD_RELEASED_OK"),
    @JsonSubTypes.Type(value = HoldReleasedRejectedReply.class, name = "HOLD_RELEASED_REJECTED"),
    @JsonSubTypes.Type(value = ConfirmOkReply.class, name = "CONFIRM_OK"),
    @JsonSubTypes.Type(value = ConfirmRejectedReply.class, name = "CONFIRM_REJECTED"),

    // V2 Replies: Waitlist
    @JsonSubTypes.Type(value = WaitlistJoinedOkReply.class, name = "WAITLIST_JOINED_OK"),
    @JsonSubTypes.Type(value = WaitlistJoinedRejectedReply.class, name = "WAITLIST_JOINED_REJECTED"),
    @JsonSubTypes.Type(value = WaitlistLeftOkReply.class, name = "WAITLIST_LEFT_OK"),
    @JsonSubTypes.Type(value = WaitlistStatusReply.class, name = "WAITLIST_STATUS_REPLY"),

    // V2 Replies: Tickets & History
    @JsonSubTypes.Type(value = TicketReply.class, name = "TICKET_REPLY"),
    @JsonSubTypes.Type(value = BookingHistoryReply.class, name = "BOOKING_HISTORY_REPLY"),

    // V2 Replies: Admin
    @JsonSubTypes.Type(value = AdminShowAddedReply.class, name = "ADMIN_SHOW_ADDED"),
    @JsonSubTypes.Type(value = AdminSeatUpdatedReply.class, name = "ADMIN_SEAT_UPDATED"),
    @JsonSubTypes.Type(value = AdminAuditLogsReply.class, name = "ADMIN_AUDIT_LOGS_REPLY"),
    @JsonSubTypes.Type(value = AdminHoldCancelledReply.class, name = "ADMIN_HOLD_CANCELLED"),

    // V2 Replies: Metrics & Pub/Sub
    @JsonSubTypes.Type(value = ServerMetricsReply.class, name = "SERVER_METRICS_REPLY"),
    @JsonSubTypes.Type(value = SubscribeOkReply.class, name = "SUBSCRIBE_OK"),
    @JsonSubTypes.Type(value = UnsubscribeOkReply.class, name = "UNSUBSCRIBE_OK"),

    // V1 Events
    @JsonSubTypes.Type(value = SeatUpdatedEvent.class, name = "SEAT_UPDATED"),

    // V2 Events
    @JsonSubTypes.Type(value = SeatHeldEvent.class, name = "SEAT_HELD_EVENT"),
    @JsonSubTypes.Type(value = SeatReleasedEvent.class, name = "SEAT_RELEASED_EVENT"),
    @JsonSubTypes.Type(value = WaitlistPromotedEvent.class, name = "WAITLIST_PROMOTED_EVENT"),
    @JsonSubTypes.Type(value = ShowUpdatedEvent.class, name = "SHOW_UPDATED_EVENT"),
    @JsonSubTypes.Type(value = MetricsUpdatedEvent.class, name = "METRICS_UPDATED_EVENT")
})
public interface Message {
    /**
     * @return Message type identifier
     */
    MessageType getType();
}
