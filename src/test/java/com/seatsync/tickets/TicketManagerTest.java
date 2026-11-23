package com.seatsync.tickets;

import com.seatsync.common.model.BookingHistoryItem;
import com.seatsync.common.model.PagedResult;
import com.seatsync.common.model.Ticket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TicketManager Unit Tests (U09)")
class TicketManagerTest {

    private TicketManager manager;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-11-23T12:00:00Z"), ZoneId.of("UTC"));
        manager = new TicketManager("test-salt", fixedClock);
    }

    @Test
    @DisplayName("Issues ticket with unguessable ID and valid cryptographic SHA-256 hash")
    void testIssueAndVerifyTicket() {
        Ticket ticket = manager.issueTicket("BKG-001", "USR-ALICE", "SHOW-01", "A1", 1500);

        assertNotNull(ticket);
        assertTrue(ticket.ticketId().startsWith("TCK-"));
        assertEquals("BKG-001", ticket.bookingId());
        assertEquals("USR-ALICE", ticket.userId());
        assertEquals("SHOW-01", ticket.showId());
        assertEquals("A1", ticket.seatId());
        assertEquals(1500, ticket.priceInCents());
        assertNotNull(ticket.verificationHash());

        // Verify valid cryptographic checksum
        assertTrue(manager.verifyTicket(ticket.ticketId(), ticket.verificationHash()));

        // Tampered checksum should fail verification
        assertFalse(manager.verifyTicket(ticket.ticketId(), "tampered-bad-hash"));
    }

    @Test
    @DisplayName("Enforces ticket ownership: owner and admin succeed; third-party customer is rejected")
    void testTicketOwnershipAccess() {
        Ticket ticket = manager.issueTicket("BKG-002", "USR-ALICE", "SHOW-01", "B2", 1200);

        // 1. Owner retrieval succeeds
        Optional<Ticket> ownerTicket = manager.getTicket(ticket.ticketId(), "USR-ALICE");
        assertTrue(ownerTicket.isPresent());
        assertEquals(ticket.ticketId(), ownerTicket.get().ticketId());

        // 2. Admin retrieval succeeds
        Optional<Ticket> adminTicket = manager.getTicket(ticket.ticketId(), "ADMIN");
        assertTrue(adminTicket.isPresent());

        // 3. Other customer retrieval is rejected
        Optional<Ticket> intruderTicket = manager.getTicket(ticket.ticketId(), "USR-BOB");
        assertTrue(intruderTicket.isEmpty(), "Unauthorized customer must not retrieve other user's ticket");
    }

    @Test
    @DisplayName("Cancellation invalidates ticket and updates booking history status")
    void testCancellationInvalidatesTicket() {
        Ticket ticket = manager.issueTicket("BKG-003", "USR-ALICE", "SHOW-01", "C3", 2000);
        assertTrue(manager.verifyTicket(ticket.ticketId(), ticket.verificationHash()));

        // Cancel booking
        manager.invalidateBookingTicket("BKG-003", "USR-ALICE");

        // Verification should now fail
        assertFalse(manager.verifyTicket(ticket.ticketId(), ticket.verificationHash()));

        // Retrieval should return empty
        assertTrue(manager.getTicket(ticket.ticketId(), "USR-ALICE").isEmpty());

        // History item should reflect CANCELLED status
        PagedResult<BookingHistoryItem> history = manager.getBookingHistory("USR-ALICE", 0, 10);
        assertEquals(1, history.items().size());
        assertEquals("CANCELLED", history.items().get(0).status());
    }

    @Test
    @DisplayName("Provides bounded pagination for booking history")
    void testBookingHistoryPagination() {
        for (int i = 1; i <= 25; i++) {
            manager.issueTicket("BKG-" + i, "USR-PAGED", "SHOW-01", "A" + i, 1000);
        }

        // Page 0 (size 10)
        PagedResult<BookingHistoryItem> p0 = manager.getBookingHistory("USR-PAGED", 0, 10);
        assertEquals(10, p0.items().size());
        assertEquals(0, p0.pageNumber());
        assertEquals(10, p0.pageSize());
        assertEquals(25, p0.totalItems());
        assertTrue(p0.hasMore());

        // Page 2 (size 10) -> last 5 items
        PagedResult<BookingHistoryItem> p2 = manager.getBookingHistory("USR-PAGED", 2, 10);
        assertEquals(5, p2.items().size());
        assertEquals(2, p2.pageNumber());
        assertFalse(p2.hasMore());

        // Maximum page size cap test
        PagedResult<BookingHistoryItem> capped = manager.getBookingHistory("USR-PAGED", 0, 100);
        assertEquals(PagedResult.MAX_PAGE_SIZE, capped.pageSize());
    }
}
