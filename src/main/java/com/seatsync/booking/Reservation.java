package com.seatsync.booking;

/** Server-only ownership record. Never sent in seat snapshots. */
record Reservation(String bookingId, String showId, String seatId, String userId) {}
