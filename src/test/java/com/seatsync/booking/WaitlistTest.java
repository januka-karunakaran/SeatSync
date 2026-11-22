package com.seatsync.booking;

import com.seatsync.client.ClientMessageHandler;
import com.seatsync.client.TcpClient;
import com.seatsync.common.model.*;
import com.seatsync.common.protocol.ProtocolJson;
import com.seatsync.common.protocol.event.WaitlistPromotedEvent;
import com.seatsync.common.protocol.reply.*;
import com.seatsync.common.protocol.request.*;
import com.seatsync.concurrency.BookingRequestProcessor;
import com.seatsync.concurrency.WorkerRequestDispatcher;
import com.seatsync.network.*;
import com.seatsync.security.AuthorizationService;
import com.seatsync.security.SecurityRequestDispatcher;
import com.seatsync.common.contracts.AuthService;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WaitlistTest {
    private static InMemoryReservationService oneSeat(AtomicLong time) {
        var service = new InMemoryReservationService(time::get);
        service.registerShow(new ShowDetails("solo", "Solo", "", "", "", "", "Hall",
            new AuditoriumLayout("one", "Hall", 1, 1, 1, List.of())));
        return service;
    }

    @Test
    void offersAdvanceInOrderWithoutMakingTheSeatPublic() {
        var time = new AtomicLong(1000);
        var service = oneSeat(time);
        List<WaitlistPromotedEvent> offers = new ArrayList<>();
        service.addOfferListener(offers::add);
        assertFalse(service.joinWaitlist("bob", "solo").success());
        var held = service.holdSeat("alice", "solo", "A1", 5000);
        assertTrue(held.success());
        assertEquals(1, service.joinWaitlist("bob", "solo").position());
        assertEquals(ErrorCode.ALREADY_ON_WAITLIST, service.joinWaitlist("bob", "solo").errorCode());
        service.joinWaitlist("carol", "solo");
        service.joinWaitlist("dan", "solo");
        assertTrue(service.leaveWaitlist("carol", "solo").success());
        assertEquals(2, service.getQueuePosition("dan", "solo"));
        time.set(held.expiresAt());
        service.expireHolds();
        var first = offers.get(0);
        assertEquals("bob", first.promotedUserId());
        assertEquals(1, service.getQueuePosition("dan", "solo"));
        assertEquals(ErrorCode.SEAT_ALREADY_HELD, service.book("eve", "solo", "A1").errorCode());
        assertEquals(ErrorCode.NOT_HOLD_OWNER, service.confirmHold("eve", first.holdId(), "x").errorCode());
        time.set(first.expiresAt());
        service.expireHolds();
        var second = offers.get(1);
        assertEquals("dan", second.promotedUserId());
        assertTrue(service.releaseHold("dan", second.holdId()).success());
        assertEquals(SeatStatus.AVAILABLE, service.snapshot("solo").seats().get(0).status());
        assertEquals(2, offers.size());
    }

    private static BlockingQueue<WaitlistPromotedEvent> listen(TcpClient client) {
        var queue = new LinkedBlockingQueue<WaitlistPromotedEvent>();
        client.getMessageHandler().addListener(new ClientMessageHandler.Listener() {
            @Override public void onWaitlistPromoted(WaitlistPromotedEvent event) { queue.add(event); }
        });
        return queue;
    }

    @Test
    void onlyPromotedClientReceivesOfferAndCanConfirmOverTcp() throws Exception {
        var service = oneSeat(new AtomicLong(1000));
        AuthService auth = (u, p) -> AuthResult.ok(u, u, "ok");
        var gate = new SecurityRequestDispatcher(auth, new AuthorizationService(), new BookingRequestProcessor(auth, service));
        try (var dispatcher = new WorkerRequestDispatcher(gate)) {
            var server = new NioBookingServer(ServerConfig.of("127.0.0.1", 0), dispatcher);
            service.addOfferListener(e -> server.getSessionRegistry().getAll()
                .forEach(s -> s.sendToUser(e.promotedUserId(), ProtocolJson.serialize(e))));
            try {
                server.start();
                try (var alice = new TcpClient("127.0.0.1", server.getPort());
                     var bob = new TcpClient("127.0.0.1", server.getPort())) {
                    alice.connect(2000);
                    bob.connect(2000);
                    var aliceOffers = listen(alice);
                    var bobOffers = listen(bob);
                    alice.sendRequest(new LoginRequest("la", "alice", "demo")).get(5, TimeUnit.SECONDS);
                    bob.sendRequest(new LoginRequest("lb", "bob", "demo")).get(5, TimeUnit.SECONDS);
                    var hold = assertInstanceOf(HoldOkReply.class, alice.sendRequest(
                        new HoldSeatRequest("hold", "solo", "A1", 120)).get(5, TimeUnit.SECONDS));
                    var booking = assertInstanceOf(ConfirmOkReply.class, alice.sendRequest(
                        new ConfirmHoldRequest("buy", hold.holdId(), "key")).get(5, TimeUnit.SECONDS));
                    assertInstanceOf(WaitlistJoinedOkReply.class, bob.sendRequest(
                        new JoinWaitlistRequest("join", "solo")).get(5, TimeUnit.SECONDS));
                    assertInstanceOf(CancelOkReply.class, alice.sendRequest(
                        new CancelBookingRequest("cancel", booking.bookingId())).get(5, TimeUnit.SECONDS));
                    var offer = bobOffers.poll(5, TimeUnit.SECONDS);
                    assertNotNull(offer);
                    assertEquals("bob", offer.promotedUserId());
                    var rejected = assertInstanceOf(ConfirmRejectedReply.class, alice.sendRequest(
                        new ConfirmHoldRequest("steal", offer.holdId(), "wrong")).get(5, TimeUnit.SECONDS));
                    assertEquals(ErrorCode.NOT_HOLD_OWNER, rejected.errorCode());
                    assertTrue(aliceOffers.isEmpty());
                    assertInstanceOf(ConfirmOkReply.class, bob.sendRequest(
                        new ConfirmHoldRequest("accept", offer.holdId(), "accept")).get(5, TimeUnit.SECONDS));
                }
            } finally {
                server.stop();
            }
        }
    }
}
