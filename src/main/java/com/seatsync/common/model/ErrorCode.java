package com.seatsync.common.model;

/**
 * Standard server-side error codes returned across protocol responses.
 * Enhanced in SeatSync 2.0 with holds, waitlists, roles, and administrative error categories.
 */
public enum ErrorCode {
    // V1 Error Codes
    AUTH_REQUIRED,
    INVALID_CREDENTIALS,
    ALREADY_AUTHENTICATED,
    SEAT_NOT_FOUND,
    SEAT_ALREADY_BOOKED,
    BOOKING_NOT_FOUND,
    NOT_BOOKING_OWNER,
    INVALID_REQUEST_FORMAT,
    UNKNOWN_REQUEST_TYPE,
    PAYLOAD_TOO_LARGE,
    SHOW_NOT_FOUND,
    RATE_LIMITED,
    INTERNAL_ERROR,

    // SeatSync 2.0 Error Codes
    SHOW_ALREADY_EXISTS,
    SEAT_ALREADY_HELD,
    HOLD_EXPIRED,
    HOLD_NOT_FOUND,
    NOT_HOLD_OWNER,
    WAITLIST_FULL,
    ALREADY_ON_WAITLIST,
    NOT_ON_WAITLIST,
    FORBIDDEN_ROLE,
    IDEMPOTENCY_CONFLICT,
    INVALID_PAGE_SIZE,
    TICKET_NOT_FOUND
}
