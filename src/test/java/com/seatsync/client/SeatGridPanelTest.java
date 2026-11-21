package com.seatsync.client;

import com.seatsync.common.model.ProtocolConstants;
import com.seatsync.common.model.SeatStatus;
import com.seatsync.common.model.SeatView;
import com.seatsync.ui.SeatGridPanel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Headless-safe unit test for {@link SeatGridPanel}.
 * Verifies seat initialization, selection, status updates, and user attribution.
 * <p>
 * Owned by Member 01 (TCP Socket Programming & Desktop Client).
 */
class SeatGridPanelTest {

    private SeatGridPanel panel;

    @BeforeEach
    void setUp() {
        // System.setProperty("java.awt.headless", "true");
        panel = new SeatGridPanel();
    }

    @Test
    @DisplayName("All 40 canonical seats (A1 to E8) are initialized as AVAILABLE")
    void testAll40SeatsInitialized() {
        for (String seatId : ProtocolConstants.VALID_SEAT_IDS) {
            SeatView view = panel.getSeatView(seatId);
            assertNotNull(view, "Seat " + seatId + " must exist");
            assertEquals(SeatStatus.AVAILABLE, view.status());
            assertNull(view.bookedBy());
        }
    }

    @Test
    @DisplayName("Selecting a seat updates selectedSeatId and fires listener callback")
    void testSeatSelection() {
        AtomicReference<String> selected = new AtomicReference<>();
        panel.addSelectionListener(selected::set);

        panel.selectSeat("C4");
        assertEquals("C4", panel.getSelectedSeatId());
        assertEquals("C4", selected.get());

        panel.clearSelection();
        assertNull(panel.getSelectedSeatId());
        assertNull(selected.get());
    }

    @Test
    @DisplayName("Updating seat state reflects BOOKED and bookedBy user")
    void testUpdateSeats() {
        panel.setCurrentUserId("USR-101");

        SeatView bookedByMe = new SeatView("B2", SeatStatus.BOOKED, "USR-101");
        SeatView bookedByOther = new SeatView("B3", SeatStatus.BOOKED, "USR-202");

        panel.updateSeats(List.of(bookedByMe, bookedByOther));

        assertEquals(SeatStatus.BOOKED, panel.getSeatView("B2").status());
        assertEquals("USR-101", panel.getSeatView("B2").bookedBy());

        assertEquals(SeatStatus.BOOKED, panel.getSeatView("B3").status());
        assertEquals("USR-202", panel.getSeatView("B3").bookedBy());
    }
}
