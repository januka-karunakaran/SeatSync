package com.seatsync.common.protocol;

/**
 * Enumeration of all client request, server reply, and asynchronous server event types.
 * Fully backwards-compatible with v1, extended with SeatSync 2.0 message definitions.
 */
public enum MessageType {
    // V1 Client Requests
    LOGIN,
    GET_SEATS,
    BOOK_SEAT,
    CANCEL_BOOKING,
    LOGOUT,

    // V2 Client Requests: Catalog & Shows
    GET_SHOWS,
    GET_SHOW_DETAILS,

    // V2 Client Requests: Two-Phase Seat Holds
    HOLD_SEAT,
    RELEASE_HOLD,
    CONFIRM_HOLD,

    // V2 Client Requests: Waitlist
    JOIN_WAITLIST,
    LEAVE_WAITLIST,
    GET_WAITLIST_STATUS,

    // V2 Client Requests: Tickets & History
    GET_TICKET,
    GET_BOOKING_HISTORY,

    // V2 Client Requests: Admin & Governance
    ADMIN_ADD_SHOW,
    ADMIN_UPDATE_SEAT_STATUS,
    ADMIN_GET_AUDIT_LOGS,
    ADMIN_CANCEL_HOLD,

    // V2 Client Requests: Telemetry & Pub/Sub
    GET_SERVER_METRICS,
    SUBSCRIBE_EVENTS,
    UNSUBSCRIBE_EVENTS,

    // V1 Server Replies
    LOGIN_OK,
    LOGIN_FAILED,
    SEAT_SNAPSHOT,
    BOOK_OK,
    BOOK_REJECTED,
    CANCEL_OK,
    CANCEL_REJECTED,
    LOGOUT_OK,
    ERROR,

    // V2 Server Replies: Catalog & Shows
    SHOW_LIST_REPLY,
    SHOW_DETAILS_REPLY,

    // V2 Server Replies: Holds
    HOLD_OK,
    HOLD_REJECTED,
    HOLD_RELEASED_OK,
    HOLD_RELEASED_REJECTED,
    CONFIRM_OK,
    CONFIRM_REJECTED,

    // V2 Server Replies: Waitlist
    WAITLIST_JOINED_OK,
    WAITLIST_JOINED_REJECTED,
    WAITLIST_LEFT_OK,
    WAITLIST_STATUS_REPLY,

    // V2 Server Replies: Tickets & History
    TICKET_REPLY,
    BOOKING_HISTORY_REPLY,

    // V2 Server Replies: Admin
    ADMIN_SHOW_ADDED,
    ADMIN_SEAT_UPDATED,
    ADMIN_AUDIT_LOGS_REPLY,
    ADMIN_HOLD_CANCELLED,

    // V2 Server Replies: Metrics & Pub/Sub
    SERVER_METRICS_REPLY,
    SUBSCRIBE_OK,
    UNSUBSCRIBE_OK,

    // V1 Server Events
    SEAT_UPDATED,

    // V2 Server Asynchronous Events
    SEAT_HELD_EVENT,
    SEAT_RELEASED_EVENT,
    WAITLIST_PROMOTED_EVENT,
    SHOW_UPDATED_EVENT,
    METRICS_UPDATED_EVENT
}
