package com.seatsync.common.model;

/**
 * Represents the current status of a seat in the auditorium.
 * In SeatSync 2.0, seats support two-phase temporary holds (HELD).
 */
public enum SeatStatus {
    AVAILABLE,
    HELD,
    BOOKED
}
