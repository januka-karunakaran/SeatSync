package com.seatsync.common;

import com.seatsync.common.contracts.ClockService;
import com.seatsync.common.model.AuditoriumLayout;
import com.seatsync.common.model.AuditLogEntry;
import com.seatsync.common.model.BookingHistoryItem;
import com.seatsync.common.model.ErrorCode;
import com.seatsync.common.model.PagedResult;
import com.seatsync.common.model.ProtocolConstants;
import com.seatsync.common.model.SeatHold;
import com.seatsync.common.model.SeatStatus;
import com.seatsync.common.model.SeatTier;
import com.seatsync.common.model.ServerMetricSnapshot;
import com.seatsync.common.model.ShowDetails;
import com.seatsync.common.model.ShowSummary;
import com.seatsync.common.model.TierType;
import com.seatsync.common.model.Ticket;
import com.seatsync.common.model.UdpBeaconPayload;
import com.seatsync.common.protocol.Message;
import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.ProtocolJson;
import com.seatsync.common.protocol.event.MetricsUpdatedEvent;
import com.seatsync.common.protocol.event.SeatHeldEvent;
import com.seatsync.common.protocol.event.SeatReleasedEvent;
import com.seatsync.common.protocol.event.ShowUpdatedEvent;
import com.seatsync.common.protocol.event.WaitlistPromotedEvent;
import com.seatsync.common.protocol.reply.AdminAuditLogsReply;
import com.seatsync.common.protocol.reply.AdminHoldCancelledReply;
import com.seatsync.common.protocol.reply.AdminSeatUpdatedReply;
import com.seatsync.common.protocol.reply.AdminShowAddedReply;
import com.seatsync.common.protocol.reply.BookingHistoryReply;
import com.seatsync.common.protocol.reply.ConfirmOkReply;
import com.seatsync.common.protocol.reply.ConfirmRejectedReply;
import com.seatsync.common.protocol.reply.HoldOkReply;
import com.seatsync.common.protocol.reply.HoldRejectedReply;
import com.seatsync.common.protocol.reply.HoldReleasedOkReply;
import com.seatsync.common.protocol.reply.HoldReleasedRejectedReply;
import com.seatsync.common.protocol.reply.ServerMetricsReply;
import com.seatsync.common.protocol.reply.ShowDetailsReply;
import com.seatsync.common.protocol.reply.ShowListReply;
import com.seatsync.common.protocol.reply.SubscribeOkReply;
import com.seatsync.common.protocol.reply.TicketReply;
import com.seatsync.common.protocol.reply.UnsubscribeOkReply;
import com.seatsync.common.protocol.reply.WaitlistJoinedOkReply;
import com.seatsync.common.protocol.reply.WaitlistJoinedRejectedReply;
import com.seatsync.common.protocol.reply.WaitlistLeftOkReply;
import com.seatsync.common.protocol.reply.WaitlistStatusReply;
import com.seatsync.common.protocol.request.AdminAddShowRequest;
import com.seatsync.common.protocol.request.AdminCancelHoldRequest;
import com.seatsync.common.protocol.request.AdminGetAuditLogsRequest;
import com.seatsync.common.protocol.request.AdminUpdateSeatStatusRequest;
import com.seatsync.common.protocol.request.ConfirmHoldRequest;
import com.seatsync.common.protocol.request.GetBookingHistoryRequest;
import com.seatsync.common.protocol.request.GetServerMetricsRequest;
import com.seatsync.common.protocol.request.GetShowDetailsRequest;
import com.seatsync.common.protocol.request.GetShowsRequest;
import com.seatsync.common.protocol.request.GetTicketRequest;
import com.seatsync.common.protocol.request.GetWaitlistStatusRequest;
import com.seatsync.common.protocol.request.HoldSeatRequest;
import com.seatsync.common.protocol.request.JoinWaitlistRequest;
import com.seatsync.common.protocol.request.LeaveWaitlistRequest;
import com.seatsync.common.protocol.request.ReleaseHoldRequest;
import com.seatsync.common.protocol.request.SubscribeEventsRequest;
import com.seatsync.common.protocol.request.UnsubscribeEventsRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract verification test suite for SeatSync 2.0 messages, serialization rules,
 * wire framing bounds, session-bound user identity, and deterministic TTL testing.
 */
class V2ProtocolContractTest {

    @Test
    @DisplayName("Catalog: GET_SHOWS and GET_SHOW_DETAILS roundtrip cleanly")
    void testCatalogRequestsAndReplies() {
        // GET_SHOWS
        GetShowsRequest getShows = new GetShowsRequest("req-cat-01");
        String json1 = ProtocolJson.serialize(getShows);
        Message msg1 = ProtocolJson.deserializeMessage(json1);
        assertInstanceOf(GetShowsRequest.class, msg1);
        assertEquals("req-cat-01", ((GetShowsRequest) msg1).requestId());

        // SHOW_LIST_REPLY
        List<ShowSummary> shows = List.of(
            new ShowSummary("SHOW-01", "The Phantom of the Opera", "2026-12-01T19:30:00Z", "Grand Stage", 40, 28),
            new ShowSummary("SHOW-02", "Les Misérables", "2026-12-02T20:00:00Z", "Main Hall", 120, 115)
        );
        ShowListReply showListReply = new ShowListReply("req-cat-01", shows);
        String json2 = ProtocolJson.serialize(showListReply);
        ShowListReply parsedList = (ShowListReply) ProtocolJson.deserializeMessage(json2);
        assertEquals(2, parsedList.shows().size());
        assertEquals("The Phantom of the Opera", parsedList.shows().get(0).title());

        // GET_SHOW_DETAILS
        GetShowDetailsRequest getDetails = new GetShowDetailsRequest("req-cat-02", "SHOW-01");
        GetShowDetailsRequest parsedDetailsReq = (GetShowDetailsRequest) ProtocolJson.deserializeMessage(ProtocolJson.serialize(getDetails));
        assertEquals("SHOW-01", parsedDetailsReq.showId());

        // SHOW_DETAILS_REPLY
        AuditoriumLayout layout = new AuditoriumLayout(
            "LAYOUT-01",
            "Orchestra Hall",
            5,
            8,
            40,
            List.of(new SeatTier("Standard Row", TierType.STANDARD, 2500, "Standard auditorium seat"))
        );
        ShowDetails details = new ShowDetails(
            "SHOW-01",
            "The Phantom of the Opera",
            "Classic Broadway musical",
            "Musical",
            "2026-12-01T19:30:00Z",
            "2026-12-01T22:00:00Z",
            "Grand Stage",
            layout
        );
        ShowDetailsReply detailsReply = new ShowDetailsReply("req-cat-02", details);
        String json3 = ProtocolJson.serialize(detailsReply);
        ShowDetailsReply parsedDetails = (ShowDetailsReply) ProtocolJson.deserializeMessage(json3);
        assertEquals("Musical", parsedDetails.showDetails().genre());
        assertEquals(40, parsedDetails.showDetails().layout().totalCapacity());
    }

    @Test
    @DisplayName("Two-Phase Holds: HOLD, RELEASE, and CONFIRM preserve session-bound identity and idempotency")
    void testTwoPhaseHoldAndConfirm() {
        // HOLD_SEAT: must not contain userId in wire payload
        HoldSeatRequest holdReq = new HoldSeatRequest("req-h-01", "SHOW-01", "B4", 120);
        String holdJson = ProtocolJson.serialize(holdReq);
        assertFalse(holdJson.contains("userId"), "HoldSeatRequest must not trust client-supplied userId");
        HoldSeatRequest parsedHoldReq = (HoldSeatRequest) ProtocolJson.deserializeMessage(holdJson);
        assertEquals("B4", parsedHoldReq.seatId());
        assertEquals(120, parsedHoldReq.holdDurationSeconds());

        // HOLD_OK
        HoldOkReply holdOk = new HoldOkReply("req-h-01", "HLD-9811", "SHOW-01", "B4", System.currentTimeMillis() + 120000L, "Hold confirmed for 120s");
        HoldOkReply parsedHoldOk = (HoldOkReply) ProtocolJson.deserializeMessage(ProtocolJson.serialize(holdOk));
        assertEquals("HLD-9811", parsedHoldOk.holdId());

        // HOLD_REJECTED
        HoldRejectedReply holdRej = new HoldRejectedReply("req-h-01", "SHOW-01", "B4", ErrorCode.SEAT_ALREADY_HELD, "Seat currently held by another user");
        HoldRejectedReply parsedHoldRej = (HoldRejectedReply) ProtocolJson.deserializeMessage(ProtocolJson.serialize(holdRej));
        assertEquals(ErrorCode.SEAT_ALREADY_HELD, parsedHoldRej.errorCode());

        // RELEASE_HOLD
        ReleaseHoldRequest relReq = new ReleaseHoldRequest("req-h-02", "HLD-9811");
        assertFalse(ProtocolJson.serialize(relReq).contains("userId"));
        ReleaseHoldRequest parsedRelReq = (ReleaseHoldRequest) ProtocolJson.deserializeMessage(ProtocolJson.serialize(relReq));
        assertEquals("HLD-9811", parsedRelReq.holdId());

        // HOLD_RELEASED_OK & REJECTED
        HoldReleasedOkReply relOk = new HoldReleasedOkReply("req-h-02", "HLD-9811", "SHOW-01", "B4", "Hold released");
        HoldReleasedOkReply parsedRelOk = (HoldReleasedOkReply) ProtocolJson.deserializeMessage(ProtocolJson.serialize(relOk));
        assertEquals("B4", parsedRelOk.seatId());

        HoldReleasedRejectedReply relRej = new HoldReleasedRejectedReply("req-h-02", "HLD-9811", ErrorCode.NOT_HOLD_OWNER, "User is not hold owner");
        HoldReleasedRejectedReply parsedRelRej = (HoldReleasedRejectedReply) ProtocolJson.deserializeMessage(ProtocolJson.serialize(relRej));
        assertEquals(ErrorCode.NOT_HOLD_OWNER, parsedRelRej.errorCode());

        // CONFIRM_HOLD with idempotency key
        ConfirmHoldRequest confReq = new ConfirmHoldRequest("req-h-03", "HLD-9811", "idem-uuid-992384");
        String confJson = ProtocolJson.serialize(confReq);
        assertFalse(confJson.contains("userId"));
        ConfirmHoldRequest parsedConfReq = (ConfirmHoldRequest) ProtocolJson.deserializeMessage(confJson);
        assertEquals("idem-uuid-992384", parsedConfReq.idempotencyKey());

        // CONFIRM_OK & REJECTED
        ConfirmOkReply confOk = new ConfirmOkReply("req-h-03", "BKG-5501", "TCK-8812", "SHOW-01", "B4", "Booking confirmed");
        ConfirmOkReply parsedConfOk = (ConfirmOkReply) ProtocolJson.deserializeMessage(ProtocolJson.serialize(confOk));
        assertEquals("TCK-8812", parsedConfOk.ticketId());

        ConfirmRejectedReply confRej = new ConfirmRejectedReply("req-h-03", "HLD-9811", ErrorCode.HOLD_EXPIRED, "Seat hold has expired");
        ConfirmRejectedReply parsedConfRej = (ConfirmRejectedReply) ProtocolJson.deserializeMessage(ProtocolJson.serialize(confRej));
        assertEquals(ErrorCode.HOLD_EXPIRED, parsedConfRej.errorCode());
    }

    @Test
    @DisplayName("Waitlist: JOIN, LEAVE, and STATUS roundtrip cleanly")
    void testWaitlistRequestsAndReplies() {
        JoinWaitlistRequest joinReq = new JoinWaitlistRequest("req-w-01", "SHOW-01");
        assertFalse(ProtocolJson.serialize(joinReq).contains("userId"));
        JoinWaitlistRequest parsedJoin = (JoinWaitlistRequest) ProtocolJson.deserializeMessage(ProtocolJson.serialize(joinReq));
        assertEquals("SHOW-01", parsedJoin.showId());

        WaitlistJoinedOkReply joinOk = new WaitlistJoinedOkReply("req-w-01", "WTL-001", "SHOW-01", 3, "Added to waitlist at position 3");
        WaitlistJoinedOkReply parsedJoinOk = (WaitlistJoinedOkReply) ProtocolJson.deserializeMessage(ProtocolJson.serialize(joinOk));
        assertEquals(3, parsedJoinOk.position());

        WaitlistJoinedRejectedReply joinRej = new WaitlistJoinedRejectedReply("req-w-01", "SHOW-01", ErrorCode.ALREADY_ON_WAITLIST, "User already on waitlist");
        WaitlistJoinedRejectedReply parsedJoinRej = (WaitlistJoinedRejectedReply) ProtocolJson.deserializeMessage(ProtocolJson.serialize(joinRej));
        assertEquals(ErrorCode.ALREADY_ON_WAITLIST, parsedJoinRej.errorCode());

        LeaveWaitlistRequest leaveReq = new LeaveWaitlistRequest("req-w-02", "SHOW-01");
        LeaveWaitlistRequest parsedLeave = (LeaveWaitlistRequest) ProtocolJson.deserializeMessage(ProtocolJson.serialize(leaveReq));
        assertEquals("SHOW-01", parsedLeave.showId());

        WaitlistLeftOkReply leaveOk = new WaitlistLeftOkReply("req-w-02", "SHOW-01", "Removed from waitlist");
        WaitlistLeftOkReply parsedLeaveOk = (WaitlistLeftOkReply) ProtocolJson.deserializeMessage(ProtocolJson.serialize(leaveOk));
        assertEquals("SHOW-01", parsedLeaveOk.showId());

        GetWaitlistStatusRequest statusReq = new GetWaitlistStatusRequest("req-w-03", "SHOW-01");
        GetWaitlistStatusRequest parsedStatusReq = (GetWaitlistStatusRequest) ProtocolJson.deserializeMessage(ProtocolJson.serialize(statusReq));
        assertEquals("SHOW-01", parsedStatusReq.showId());

        WaitlistStatusReply statusReply = new WaitlistStatusReply("req-w-03", "SHOW-01", 2, 8);
        WaitlistStatusReply parsedStatusReply = (WaitlistStatusReply) ProtocolJson.deserializeMessage(ProtocolJson.serialize(statusReply));
        assertEquals(2, parsedStatusReply.position());
        assertEquals(8, parsedStatusReply.totalWaitlistSize());
    }

    @Test
    @DisplayName("Tickets and History: Digital tickets and paginated query results")
    void testTicketAndBookingHistory() {
        GetTicketRequest getTicket = new GetTicketRequest("req-t-01", "TCK-1001");
        GetTicketRequest parsedTicketReq = (GetTicketRequest) ProtocolJson.deserializeMessage(ProtocolJson.serialize(getTicket));
        assertEquals("TCK-1001", parsedTicketReq.ticketId());

        Ticket ticket = new Ticket(
            "TCK-1001",
            "BKG-5501",
            "SHOW-01",
            "C3",
            "alice",
            1795140000000L,
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            3500
        );
        TicketReply ticketReply = new TicketReply("req-t-01", ticket);
        TicketReply parsedTicketReply = (TicketReply) ProtocolJson.deserializeMessage(ProtocolJson.serialize(ticketReply));
        assertEquals("C3", parsedTicketReply.ticket().seatId());
        assertEquals(3500, parsedTicketReply.ticket().priceInCents());

        // Bounded pagination: max page size = 50
        assertThrows(IllegalArgumentException.class, () -> new PagedResult<>(List.of(), 0, 51, 100L, false));
        assertThrows(IllegalArgumentException.class, () -> new PagedResult<>(List.of(), -1, 10, 100L, false));

        GetBookingHistoryRequest histReq = new GetBookingHistoryRequest("req-h-01", 0, 20);
        assertFalse(ProtocolJson.serialize(histReq).contains("userId"));
        GetBookingHistoryRequest parsedHistReq = (GetBookingHistoryRequest) ProtocolJson.deserializeMessage(ProtocolJson.serialize(histReq));
        assertEquals(20, parsedHistReq.pageSize());

        BookingHistoryItem item = new BookingHistoryItem("BKG-5501", "SHOW-01", "C3", "The Phantom of the Opera", "2026-12-01T19:30:00Z", "CONFIRMED", 1795140000000L, "TCK-1001");
        PagedResult<BookingHistoryItem> paged = new PagedResult<>(List.of(item), 0, 20, 1L, false);
        BookingHistoryReply histReply = new BookingHistoryReply("req-h-01", paged);
        BookingHistoryReply parsedHistReply = (BookingHistoryReply) ProtocolJson.deserializeMessage(ProtocolJson.serialize(histReply));
        assertEquals(1, parsedHistReply.history().items().size());
        assertEquals("CONFIRMED", parsedHistReply.history().items().get(0).status());
    }

    @Test
    @DisplayName("Admin and Governance: Show creation, seat status override, and audit trail")
    void testAdminManagementMessages() {
        AuditoriumLayout layout = new AuditoriumLayout("L-02", "Balcony Room", 4, 10, 40, List.of());
        ShowDetails newShow = new ShowDetails("SHOW-03", "Hamilton", "Revolutionary musical", "History", "2026-12-05T18:00:00Z", "2026-12-05T21:00:00Z", "Broadway Arena", layout);
        AdminAddShowRequest addReq = new AdminAddShowRequest("req-a-01", newShow);
        AdminAddShowRequest parsedAdd = (AdminAddShowRequest) ProtocolJson.deserializeMessage(ProtocolJson.serialize(addReq));
        assertEquals("Hamilton", parsedAdd.showDetails().title());

        AdminShowAddedReply addReply = new AdminShowAddedReply("req-a-01", "SHOW-03", "Show added successfully");
        AdminShowAddedReply parsedAddReply = (AdminShowAddedReply) ProtocolJson.deserializeMessage(ProtocolJson.serialize(addReply));
        assertEquals("SHOW-03", parsedAddReply.showId());

        AdminUpdateSeatStatusRequest updateSeat = new AdminUpdateSeatStatusRequest("req-a-02", "SHOW-01", "D1", SeatStatus.BOOKED);
        AdminUpdateSeatStatusRequest parsedUpdate = (AdminUpdateSeatStatusRequest) ProtocolJson.deserializeMessage(ProtocolJson.serialize(updateSeat));
        assertEquals(SeatStatus.BOOKED, parsedUpdate.status());

        AdminSeatUpdatedReply updateReply = new AdminSeatUpdatedReply("req-a-02", "SHOW-01", "D1", SeatStatus.BOOKED, "Seat updated");
        AdminSeatUpdatedReply parsedUpdateReply = (AdminSeatUpdatedReply) ProtocolJson.deserializeMessage(ProtocolJson.serialize(updateReply));
        assertEquals("D1", parsedUpdateReply.seatId());

        AdminCancelHoldRequest cancelHold = new AdminCancelHoldRequest("req-a-03", "HLD-9811", "Administrative override for vip seating");
        AdminCancelHoldRequest parsedCancelHold = (AdminCancelHoldRequest) ProtocolJson.deserializeMessage(ProtocolJson.serialize(cancelHold));
        assertEquals("HLD-9811", parsedCancelHold.holdId());

        AdminHoldCancelledReply cancelHoldReply = new AdminHoldCancelledReply("req-a-03", "HLD-9811", "Hold cancelled by admin");
        AdminHoldCancelledReply parsedCancelHoldReply = (AdminHoldCancelledReply) ProtocolJson.deserializeMessage(ProtocolJson.serialize(cancelHoldReply));
        assertEquals("HLD-9811", parsedCancelHoldReply.holdId());

        AdminGetAuditLogsRequest logsReq = new AdminGetAuditLogsRequest("req-a-04", 50);
        AdminGetAuditLogsRequest parsedLogsReq = (AdminGetAuditLogsRequest) ProtocolJson.deserializeMessage(ProtocolJson.serialize(logsReq));
        assertEquals(50, parsedLogsReq.limit());

        AuditLogEntry audit = new AuditLogEntry("LOG-1", System.currentTimeMillis(), "admin-01", "CANCEL_HOLD", "HLD-9811", "Emergency cancellation", "127.0.0.1");
        AdminAuditLogsReply logsReply = new AdminAuditLogsReply("req-a-04", List.of(audit));
        AdminAuditLogsReply parsedLogsReply = (AdminAuditLogsReply) ProtocolJson.deserializeMessage(ProtocolJson.serialize(logsReply));
        assertEquals(1, parsedLogsReply.logs().size());
        assertEquals("CANCEL_HOLD", parsedLogsReply.logs().get(0).action());
    }

    @Test
    @DisplayName("Telemetry and Discovery: Server metrics and UDP beacon payload")
    void testTelemetryAndDiscovery() {
        GetServerMetricsRequest metReq = new GetServerMetricsRequest("req-m-01");
        GetServerMetricsRequest parsedMetReq = (GetServerMetricsRequest) ProtocolJson.deserializeMessage(ProtocolJson.serialize(metReq));
        assertEquals("req-m-01", parsedMetReq.requestId());

        ServerMetricSnapshot snapshot = new ServerMetricSnapshot(3600L, 42, 125000L, 890L, 12, 5, 2.4, 3L);
        ServerMetricsReply metReply = new ServerMetricsReply("req-m-01", snapshot);
        ServerMetricsReply parsedMetReply = (ServerMetricsReply) ProtocolJson.deserializeMessage(ProtocolJson.serialize(metReply));
        assertEquals(42, parsedMetReply.metrics().activeConnections());
        assertEquals(2.4, parsedMetReply.metrics().p95LatencyMs());

        // UDP Beacon Payload
        UdpBeaconPayload beacon = new UdpBeaconPayload("SeatSync", "2.0", "127.0.0.1", 8080, 8443, 42, System.currentTimeMillis());
        String beaconJson = ProtocolJson.serialize(beacon);
        UdpBeaconPayload parsedBeacon = ProtocolJson.deserialize(beaconJson, UdpBeaconPayload.class);
        assertEquals(8443, parsedBeacon.tlsPort());
        assertEquals("2.0", parsedBeacon.version());
    }

    @Test
    @DisplayName("Pub/Sub Events: Subscribe, Unsubscribe, and all 5 Server Events")
    void testPubSubAndServerEvents() {
        SubscribeEventsRequest subReq = new SubscribeEventsRequest("req-sub-01", "SHOW-01");
        SubscribeEventsRequest parsedSub = (SubscribeEventsRequest) ProtocolJson.deserializeMessage(ProtocolJson.serialize(subReq));
        assertEquals("SHOW-01", parsedSub.showId());

        SubscribeOkReply subReply = new SubscribeOkReply("req-sub-01", "SHOW-01", "Subscribed to events for SHOW-01");
        SubscribeOkReply parsedSubReply = (SubscribeOkReply) ProtocolJson.deserializeMessage(ProtocolJson.serialize(subReply));
        assertEquals("SHOW-01", parsedSubReply.showId());

        UnsubscribeEventsRequest unsubReq = new UnsubscribeEventsRequest("req-sub-02", "SHOW-01");
        UnsubscribeEventsRequest parsedUnsub = (UnsubscribeEventsRequest) ProtocolJson.deserializeMessage(ProtocolJson.serialize(unsubReq));
        assertEquals("SHOW-01", parsedUnsub.showId());

        UnsubscribeOkReply unsubReply = new UnsubscribeOkReply("req-sub-02", "SHOW-01", "Unsubscribed");
        UnsubscribeOkReply parsedUnsubReply = (UnsubscribeOkReply) ProtocolJson.deserializeMessage(ProtocolJson.serialize(unsubReply));
        assertEquals("SHOW-01", parsedUnsubReply.showId());

        // 1. SEAT_HELD_EVENT
        SeatHeldEvent heldEvt = new SeatHeldEvent("SHOW-01", "C4", "HLD-100", 1795140120000L, 5L);
        String heldJson = ProtocolJson.serialize(heldEvt);
        assertFalse(heldJson.contains("requestId"), "Server events do not carry client requestId");
        SeatHeldEvent parsedHeldEvt = (SeatHeldEvent) ProtocolJson.deserializeMessage(heldJson);
        assertEquals("C4", parsedHeldEvt.seatId());

        // 2. SEAT_RELEASED_EVENT
        SeatReleasedEvent relEvt = new SeatReleasedEvent("SHOW-01", "C4", "HLD-100", 6L);
        SeatReleasedEvent parsedRelEvt = (SeatReleasedEvent) ProtocolJson.deserializeMessage(ProtocolJson.serialize(relEvt));
        assertEquals(6L, parsedRelEvt.version());

        // 3. WAITLIST_PROMOTED_EVENT
        WaitlistPromotedEvent promEvt = new WaitlistPromotedEvent("SHOW-01", "C4", "usr-bob", "HLD-101", 1795140240000L, 7L);
        WaitlistPromotedEvent parsedPromEvt = (WaitlistPromotedEvent) ProtocolJson.deserializeMessage(ProtocolJson.serialize(promEvt));
        assertEquals("usr-bob", parsedPromEvt.promotedUserId());

        // 4. SHOW_UPDATED_EVENT
        ShowSummary summary = new ShowSummary("SHOW-01", "The Phantom of the Opera", "2026-12-01T19:30:00Z", "Grand Stage", 40, 27);
        ShowUpdatedEvent showEvt = new ShowUpdatedEvent("SHOW-01", summary, 8L);
        ShowUpdatedEvent parsedShowEvt = (ShowUpdatedEvent) ProtocolJson.deserializeMessage(ProtocolJson.serialize(showEvt));
        assertEquals(27, parsedShowEvt.summary().availableSeats());

        // 5. METRICS_UPDATED_EVENT
        ServerMetricSnapshot snap = new ServerMetricSnapshot(120L, 10, 500L, 20L, 1, 0, 1.2, 0L);
        MetricsUpdatedEvent metEvt = new MetricsUpdatedEvent(snap, System.currentTimeMillis());
        MetricsUpdatedEvent parsedMetEvt = (MetricsUpdatedEvent) ProtocolJson.deserializeMessage(ProtocolJson.serialize(metEvt));
        assertEquals(10, parsedMetEvt.metrics().activeConnections());
    }

    @Test
    @DisplayName("Framing Security: Enforces 16 KiB frame limit and rejects malformed JSON")
    void testFramingBoundsAndMalformedJson() {
        // Wire serialization appends \n
        String wire = ProtocolJson.serializeToWire(new GetShowsRequest("req-wire-01"));
        assertTrue(wire.endsWith("\n"));

        // Reject oversized frame (> 16 KiB)
        StringBuilder oversized = new StringBuilder("{\"type\":\"ADMIN_ADD_SHOW\",\"requestId\":\"1\",\"showDetails\":{\"description\":\"");
        while (oversized.length() < ProtocolConstants.MAX_FRAME_BYTES + 100) {
            oversized.append("X");
        }
        oversized.append("\"}}");

        assertThrows(IllegalArgumentException.class, () -> {
            ProtocolJson.deserialize(oversized.toString(), Message.class);
        }, "Frames exceeding 16 KiB must throw IllegalArgumentException");

        // Reject malformed JSON
        assertThrows(IllegalArgumentException.class, () -> {
            ProtocolJson.deserializeMessage("{malformed json without quotes}");
        });
    }

    @Test
    @DisplayName("TTL Clock: Verifies deterministic hold expiration logic using test clock")
    void testDeterministicHoldExpirationWithClock() {
        AtomicLong testClockMillis = new AtomicLong(1000000L);
        ClockService testClock = testClockMillis::get;

        SeatHold hold = new SeatHold(
            "HLD-TEST-01",
            "SHOW-01",
            "A1",
            "user-100",
            testClock.currentTimeMillis(),
            testClock.currentTimeMillis() + 60000L, // 60s TTL
            false
        );

        // At T = 0, not expired
        assertFalse(hold.isExpired(testClock.currentTimeMillis()));

        // At T = +30s, not expired
        testClockMillis.addAndGet(30000L);
        assertFalse(hold.isExpired(testClock.currentTimeMillis()));

        // At T = +60s, expired!
        testClockMillis.addAndGet(30000L);
        assertTrue(hold.isExpired(testClock.currentTimeMillis()));
    }
}
