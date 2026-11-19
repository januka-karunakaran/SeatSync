package com.seatsync.common.protocol;

/**
 * Enumeration of all client request, server reply, and asynchronous server event types.
 */
public enum MessageType {
    // Client Requests
    LOGIN,
    GET_SEATS,
    BOOK_SEAT,
    CANCEL_BOOKING,
    LOGOUT,

    // Server Replies
    LOGIN_OK,
    LOGIN_FAILED,
    SEAT_SNAPSHOT,
    BOOK_OK,
    BOOK_REJECTED,
    CANCEL_OK,
    CANCEL_REJECTED,
    LOGOUT_OK,
    ERROR,

    // Server Asynchronous Events
    SEAT_UPDATED
}
