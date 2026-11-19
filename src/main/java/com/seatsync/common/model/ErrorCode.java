package com.seatsync.common.model;

/**
 * Standard server-side error codes returned across protocol responses.
 */
public enum ErrorCode {
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
    INTERNAL_ERROR
}
