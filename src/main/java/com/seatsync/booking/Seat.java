package com.seatsync.booking;

import com.seatsync.common.model.SeatView;
import com.seatsync.common.model.SeatHold;

/** Mutable inventory entry; accessed only while holding the inventory lock. */
final class Seat {
    private final String id;
    private Reservation reservation;

    private SeatHold hold;

    Seat(String id) { this.id = id; }
    boolean isAvailable() { return reservation == null && hold == null; }
    SeatHold hold() { return hold; }
    void setHold(SeatHold value) { hold = value; }
    void reserve(Reservation value) { reservation = value; }
    void release() { reservation = null; }
    Reservation reservation() { return reservation; }

    SeatView view() {
        if (hold != null) { return SeatView.held(id, null); }
        return reservation == null ? SeatView.available(id) : SeatView.booked(id, reservation.userId());
    }
}
