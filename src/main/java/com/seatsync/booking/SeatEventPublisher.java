package com.seatsync.booking;

import com.seatsync.common.contracts.SeatChangeListener;
import com.seatsync.common.model.SeatSnapshot;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/** Ordered, in-process delivery with no listener callbacks under the inventory lock. */
final class SeatEventPublisher {
    private static final Logger LOG = Logger.getLogger(SeatEventPublisher.class.getName());
    private final CopyOnWriteArrayList<SeatChangeListener> listeners = new CopyOnWriteArrayList<>();
    private final ArrayDeque<Delivery> pending = new ArrayDeque<>();
    private boolean draining;

    void add(SeatChangeListener listener) { listeners.addIfAbsent(Objects.requireNonNull(listener)); }
    void remove(SeatChangeListener listener) { listeners.remove(listener); }

    // Called under the inventory lock to preserve commit/version order.
    synchronized void enqueue(SeatSnapshot snapshot) {
        if (!listeners.isEmpty()) {
            pending.addLast(new Delivery(snapshot, List.copyOf(listeners)));
        }
    }

    // A single caller drains; concurrent/reentrant mutations enqueue for this caller.
    void drain() {
        synchronized (this) {
            if (draining) { return; }
            draining = true;
        }
        while (true) {
            Delivery delivery;
            synchronized (this) {
                delivery = pending.pollFirst();
                if (delivery == null) {
                    draining = false;
                    return;
                }
            }
            for (SeatChangeListener listener : delivery.listeners()) {
                try {
                    SeatSnapshot snapshot = delivery.snapshot();
                    listener.onSeatUpdated(snapshot.showId(), snapshot.version(), snapshot.seats());
                } catch (RuntimeException failure) {
                    LOG.warning("Seat listener failed; committed inventory is unchanged");
                }
            }
        }
    }

    private record Delivery(SeatSnapshot snapshot, List<SeatChangeListener> listeners) {}
}
