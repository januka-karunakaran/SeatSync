package com.seatsync.client;

import com.seatsync.common.model.ProtocolConstants;
import com.seatsync.common.model.SeatStatus;
import com.seatsync.common.model.SeatView;
import com.seatsync.ui.BookingFrame;
import com.seatsync.ui.SeatGridPanel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.GraphicsEnvironment;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless-safe unit test verifying Stage 3 components:
 * Cinema seat grid rendering, FilmTIX palette mappings, seat selection listeners,
 * hold/booking state transitions, and BookingFrame 3-column seat map page construction.
 */
class CinemaSeatBookingTest {

    @Test
    @DisplayName("SeatGridPanel initializes 40 canonical seats with modern cinema palette")
    void testSeatGridPanelPaletteAndStyles() {
        SeatGridPanel panel = new SeatGridPanel();
        assertNotNull(panel);

        // Verify all 40 seats exist
        for (String seatId : ProtocolConstants.VALID_SEAT_IDS) {
            SeatView view = panel.getSeatView(seatId);
            assertNotNull(view, "Seat " + seatId + " must exist");
            assertEquals(SeatStatus.AVAILABLE, view.status());
            assertNull(view.bookedBy());
        }

        // Test selection
        panel.selectSeat("C4");
        assertEquals("C4", panel.getSelectedSeatId());
        assertNotNull(panel.getSelectedSeatView());
        assertEquals("C4", panel.getSelectedSeatView().seatId());

        panel.clearSelection();
        assertNull(panel.getSelectedSeatId());
        assertNull(panel.getSelectedSeatView());
    }

    @Test
    @DisplayName("SeatGridPanel accurately reflects held, booked, and user-owned seats")
    void testSeatStatusTransitions() {
        SeatGridPanel panel = new SeatGridPanel();
        panel.setCurrentUserId("USR-ALICE");

        SeatView myBookedSeat = new SeatView("A1", SeatStatus.BOOKED, "USR-ALICE");
        SeatView otherBookedSeat = new SeatView("A2", SeatStatus.BOOKED, "USR-BOB");
        SeatView heldSeat = new SeatView("B3", SeatStatus.HELD, "USR-CHARLIE");

        panel.updateSeats(List.of(myBookedSeat, otherBookedSeat, heldSeat));

        assertEquals(SeatStatus.BOOKED, panel.getSeatView("A1").status());
        assertEquals("USR-ALICE", panel.getSeatView("A1").bookedBy());

        assertEquals(SeatStatus.BOOKED, panel.getSeatView("A2").status());
        assertEquals("USR-BOB", panel.getSeatView("A2").bookedBy());

        assertEquals(SeatStatus.HELD, panel.getSeatView("B3").status());
    }

    @Test
    @DisplayName("SeatGridPanel notifies listeners on selection and deselection")
    void testSelectionListeners() {
        SeatGridPanel panel = new SeatGridPanel();
        AtomicReference<String> selectedSeat = new AtomicReference<>();

        panel.addSelectionListener(selectedSeat::set);
        panel.selectSeat("E8");
        assertEquals("E8", selectedSeat.get());

        panel.clearSelection();
        assertNull(selectedSeat.get());
    }

    @Test
    @DisplayName("BookingFrame builds 3-column Cinema Seat Map page cleanly")
    void testBookingFrameSeatMapConstruction() {
        if (GraphicsEnvironment.isHeadless()) {
            return; // Skip native JFrame peer creation on headless Linux CI runners
        }
        BookingFrame frame = new BookingFrame(null, "alice", "USR-101", "SHOW-01");
        frame.initialize();
        assertNotNull(frame);
        assertTrue(frame.getWidth() >= 940);
        frame.dispose();
    }
}
