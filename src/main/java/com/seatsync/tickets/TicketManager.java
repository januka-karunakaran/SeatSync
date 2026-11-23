package com.seatsync.tickets;

import com.seatsync.common.contracts.HistoryService;
import com.seatsync.common.contracts.TicketService;
import com.seatsync.common.model.BookingHistoryItem;
import com.seatsync.common.model.PagedResult;
import com.seatsync.common.model.Ticket;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe ticket issuance, cryptographic verification, and booking history service.
 * <p>
 * Key capabilities:
 * <ul>
 *   <li>Issues unguessable ticket IDs with SHA-256 tamper-proof verification checksums.</li>
 *   <li>Strict role/ownership verification: customers can only view their own tickets.</li>
 *   <li>Invalidates digital tickets upon booking cancellation.</li>
 *   <li>Provides bounded, paginated user booking history queries.</li>
 * </ul>
 * <p>
 * Owned by Member 01 (Reliability, Events &amp; Asynchronous Messaging).
 */
public class TicketManager implements TicketService, HistoryService {

    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final Map<String, String> bookingToTicket = new ConcurrentHashMap<>();
    private final Map<String, List<BookingHistoryItem>> userHistory = new ConcurrentHashMap<>();
    private final Set<String> invalidatedTickets = ConcurrentHashMap.newKeySet();

    private final String secretSalt;
    private final Clock clock;

    public TicketManager() {
        this(UUID.randomUUID().toString(), Clock.systemUTC());
    }

    public TicketManager(String secretSalt, Clock clock) {
        this.secretSalt = Objects.requireNonNull(secretSalt, "secretSalt must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public Ticket issueTicket(String bookingId, String userId, String showId, String seatId, int priceInCents) {
        Objects.requireNonNull(bookingId, "bookingId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(showId, "showId must not be null");
        Objects.requireNonNull(seatId, "seatId must not be null");

        String ticketId = "TCK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        long issuedAt = clock.millis();
        String verificationHash = computeVerificationHash(ticketId, bookingId, userId, showId, seatId, priceInCents);

        Ticket ticket = new Ticket(
            ticketId,
            bookingId,
            showId,
            seatId,
            userId,
            issuedAt,
            verificationHash,
            priceInCents
        );

        tickets.put(ticketId, ticket);
        bookingToTicket.put(bookingId, ticketId);

        BookingHistoryItem historyItem = new BookingHistoryItem(
            bookingId,
            showId,
            seatId,
            "Show " + showId,
            "Scheduled",
            "CONFIRMED",
            issuedAt,
            ticketId
        );
        userHistory.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(0, historyItem);

        return ticket;
    }

    @Override
    public Optional<Ticket> getTicket(String ticketId, String userId) {
        if (ticketId == null || userId == null) {
            return Optional.empty();
        }
        Ticket ticket = tickets.get(ticketId);
        if (ticket == null) {
            return Optional.empty();
        }

        // Enforce ownership: only ticket owner or admin ("admin" or starts with "ADMIN") can retrieve ticket details
        boolean isOwner = ticket.userId().equals(userId);
        boolean isAdmin = "admin".equalsIgnoreCase(userId) || userId.toUpperCase().startsWith("ADMIN");
        if (!isOwner && !isAdmin) {
            return Optional.empty();
        }

        if (invalidatedTickets.contains(ticketId)) {
            return Optional.empty();
        }

        return Optional.of(ticket);
    }

    @Override
    public boolean verifyTicket(String ticketId, String verificationHash) {
        if (ticketId == null || verificationHash == null) {
            return false;
        }
        if (invalidatedTickets.contains(ticketId)) {
            return false;
        }

        Ticket ticket = tickets.get(ticketId);
        if (ticket == null) {
            return false;
        }

        String expected = computeVerificationHash(
            ticket.ticketId(),
            ticket.bookingId(),
            ticket.userId(),
            ticket.showId(),
            ticket.seatId(),
            ticket.priceInCents()
        );

        return expected.equalsIgnoreCase(verificationHash) && ticket.verificationHash().equalsIgnoreCase(verificationHash);
    }

    /**
     * Invalidates a ticket when its associated booking is cancelled.
     */
    public void invalidateBookingTicket(String bookingId, String userId) {
        if (bookingId == null) {
            return;
        }
        String ticketId = bookingToTicket.get(bookingId);
        if (ticketId != null) {
            invalidatedTickets.add(ticketId);
        }

        if (userId != null) {
            List<BookingHistoryItem> list = userHistory.get(userId);
            if (list != null) {
                for (int i = 0; i < list.size(); i++) {
                    BookingHistoryItem item = list.get(i);
                    if (bookingId.equals(item.bookingId())) {
                        BookingHistoryItem cancelled = new BookingHistoryItem(
                            item.bookingId(),
                            item.showId(),
                            item.seatId(),
                            item.showTitle(),
                            item.showTime(),
                            "CANCELLED",
                            clock.millis(),
                            item.ticketId()
                        );
                        list.set(i, cancelled);
                        break;
                    }
                }
            }
        }
    }

    @Override
    public PagedResult<BookingHistoryItem> getBookingHistory(String userId, int pageNumber, int pageSize) {
        if (userId == null) {
            return new PagedResult<>(List.of(), 0, PagedResult.DEFAULT_PAGE_SIZE, 0, false);
        }

        int boundedPageSize = Math.min(Math.max(1, pageSize), PagedResult.MAX_PAGE_SIZE);
        int boundedPageNumber = Math.max(0, pageNumber);

        List<BookingHistoryItem> all = userHistory.getOrDefault(userId, Collections.emptyList());
        int totalItems = all.size();
        int startIndex = boundedPageNumber * boundedPageSize;

        if (startIndex >= totalItems) {
            return new PagedResult<>(List.of(), boundedPageNumber, boundedPageSize, totalItems, false);
        }

        int endIndex = Math.min(startIndex + boundedPageSize, totalItems);
        List<BookingHistoryItem> pageItems = new ArrayList<>(all.subList(startIndex, endIndex));
        boolean hasMore = endIndex < totalItems;

        return new PagedResult<>(pageItems, boundedPageNumber, boundedPageSize, totalItems, hasMore);
    }

    private String computeVerificationHash(String ticketId, String bookingId, String userId,
                                           String showId, String seatId, int priceInCents) {
        String raw = ticketId + ":" + bookingId + ":" + userId + ":" + showId + ":" + seatId + ":"
            + priceInCents + ":" + secretSalt;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public int totalIssuedCount() {
        return tickets.size();
    }

    public void clear() {
        tickets.clear();
        bookingToTicket.clear();
        userHistory.clear();
        invalidatedTickets.clear();
    }
}
