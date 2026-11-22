package com.seatsync.booking;

import com.seatsync.common.model.SeatView;

/** Mutable inventory entry; accessed only while holding the inventory lock. */
final class Seat {
    private final String id;
    private Reservation reservation;

    Seat(String id) { this.id = id; }
    boolean isAvailable() { return reservation == null; }
    void reserve(Reservation value) { reservation = value; }
    void release() { reservation = null; }

    SeatView view() {
        return reservation == null ? SeatView.available(id) : SeatView.booked(id, reservation.userId());
    }
}
