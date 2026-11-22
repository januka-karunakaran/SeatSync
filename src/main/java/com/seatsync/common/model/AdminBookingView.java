package com.seatsync.common.model;

/** Administrative active-booking projection. Never expose through customer catalog responses. */
public record AdminBookingView(String bookingId, String showId, String seatId, String userId) {}
