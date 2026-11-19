package com.seatsync.common.contracts;

import com.seatsync.common.model.SeatView;
import java.util.List;

/**
 * Event listener interface notified whenever seat availability changes in the booking engine,
 * enabling asynchronous broadcast of SEAT_UPDATED events to connected clients.
 */
@FunctionalInterface
public interface SeatChangeListener {

    /**
     * Invoked when seat state updates occur.
     *
     * @param showId  Target show ID
     * @param version Updated state revision number
     * @param seats   Updated or complete seat views
     */
    void onSeatUpdated(String showId, long version, List<SeatView> seats);
}
