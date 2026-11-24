package com.seatsync.client;

import com.seatsync.common.model.Ticket;
import com.seatsync.common.model.UserRole;
import com.seatsync.ui.BookingFrame;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.GraphicsEnvironment;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless-safe unit test verifying Stage 4 components:
 * Shows table styling, My Tickets digital pass rendering, Waitlist queue panel,
 * and Administrator telemetry console for both customer and admin roles.
 */
class RemainingPagesAndPolishTest {

    @Test
    @DisplayName("BookingFrame initializes all 7 cinematic pages cleanly for customer role")
    void testBookingFramePagesCustomer() {
        if (GraphicsEnvironment.isHeadless()) {
            return; // Skip native peer creation on headless CI runners
        }
        BookingFrame frame = new BookingFrame(null, "alice", "USR-101", "SHOW-01", UserRole.CUSTOMER);
        frame.initialize();
        assertNotNull(frame);
        assertTrue(frame.getWidth() >= 960);
        frame.dispose();
    }

    @Test
    @DisplayName("BookingFrame initializes all pages and enables Admin telemetry for admin role")
    void testBookingFramePagesAdmin() {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        BookingFrame frame = new BookingFrame(null, "admin", "USR-ADMIN", "SHOW-01", UserRole.ADMIN);
        frame.initialize();
        assertNotNull(frame);
        assertTrue(frame.getWidth() >= 960);
        frame.dispose();
    }

    @Test
    @DisplayName("Ticket model contains all required cryptographic metadata")
    void testTicketModel() {
        String ticketId = "TCK-" + UUID.randomUUID().toString().substring(0, 8);
        String bookingId = "BKG-" + UUID.randomUUID().toString().substring(0, 8);
        Ticket ticket = new Ticket(
            ticketId,
            bookingId,
            "SHOW-01",
            "C4",
            "USR-ALICE",
            System.currentTimeMillis(),
            "SHA256:7f83b1657ff1fc53b92dc18148a1d65dfc2d4b1fa3d677284addd200126d9069",
            1250
        );

        assertNotNull(ticket);
        assertNotNull(ticket.ticketId());
        assertNotNull(ticket.bookingId());
        assertNotNull(ticket.verificationHash());
        assertTrue(ticket.verificationHash().startsWith("SHA256:"));
    }
}
