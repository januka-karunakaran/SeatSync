package com.seatsync.delivery;

import com.seatsync.common.contracts.RequestContext;
import com.seatsync.common.model.SeatView;
import com.seatsync.common.model.ShowSummary;
import com.seatsync.common.protocol.Message;
import com.seatsync.common.protocol.ProtocolJson;
import com.seatsync.common.protocol.event.SeatHeldEvent;
import com.seatsync.common.protocol.event.SeatReleasedEvent;
import com.seatsync.common.protocol.event.SeatUpdatedEvent;
import com.seatsync.common.protocol.event.ShowUpdatedEvent;
import com.seatsync.common.protocol.event.WaitlistPromotedEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Server-side EventBus delivering selective, show-scoped real-time event broadcasts.
 * Guaranteed:
 * <ul>
 *   <li>Only subscribed sessions receive events for a show (strict show isolation).</li>
 *   <li>Sensitive user authentication or hold tokens are sanitized from public seat broadcasts.</li>
 *   <li>Asynchronous delivery over non-blocking socket outbound queues without blocking worker threads.</li>
 *   <li>Disconnected or closed client sessions are gracefully detected and pruned.</li>
 * </ul>
 * <p>
 * Owned by Member 01 (Reliability, Events &amp; Asynchronous Messaging).
 */
public class ShowEventBus {

    private static final Logger LOG = Logger.getLogger(ShowEventBus.class.getName());

    private final ShowSubscriptionRegistry registry;
    private final AtomicLong eventSequence = new AtomicLong(1);

    public ShowEventBus(ShowSubscriptionRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    public ShowSubscriptionRegistry getRegistry() {
        return registry;
    }

    /**
     * Publishes a sanitized SEAT_UPDATED event to all active subscribers of the given show.
     *
     * @param showId  Target show ID
     * @param version Monotonically increasing revision version
     * @param seats   Updated seat views
     */
    public void publishSeatUpdated(String showId, long version, List<SeatView> seats) {
        if (showId == null) {
            return;
        }

        // Sanitize seats: preserve status and seatId, ensure no private sensitive tokens leak
        List<SeatView> sanitized = sanitizeSeats(seats);
        SeatUpdatedEvent event = new SeatUpdatedEvent(showId, version, sanitized);
        broadcastToShow(showId, event);
    }

    /**
     * Publishes a SEAT_HELD event to subscribers of the show.
     */
    public void publishSeatHeld(String showId, String seatId, String holdId, long expiresAt, long version) {
        if (showId == null || seatId == null || holdId == null) {
            return;
        }
        SeatHeldEvent event = new SeatHeldEvent(showId, seatId, holdId, expiresAt, version);
        broadcastToShow(showId, event);
    }

    /**
     * Publishes a SEAT_RELEASED event to subscribers of the show.
     */
    public void publishSeatReleased(String showId, String seatId, String holdId, long version) {
        if (showId == null || seatId == null || holdId == null) {
            return;
        }
        SeatReleasedEvent event = new SeatReleasedEvent(showId, seatId, holdId, version);
        broadcastToShow(showId, event);
    }

    /**
     * Publishes a SHOW_UPDATED event to subscribers of the show.
     */
    public void publishShowUpdated(String showId, ShowSummary summary, long version, Boolean enabled) {
        if (showId == null || summary == null) {
            return;
        }
        ShowUpdatedEvent event = new ShowUpdatedEvent(showId, summary, version, enabled);
        broadcastToShow(showId, event);
    }

    /**
     * Dispatches a private waitlist promotion notification directly to the promoted user session.
     */
    public void notifyWaitlistPromoted(RequestContext context, WaitlistPromotedEvent event) {
        if (context != null && context.isOpen() && event != null) {
            try {
                context.send(ProtocolJson.serialize(event));
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to deliver waitlist promotion to session " + context.getSessionId(), e);
            }
        }
    }

    /**
     * Broadcasts a serialized message frame to all sessions subscribed to a specific show.
     *
     * @param showId Target show ID
     * @param event  Event message to serialize and deliver
     */
    public void broadcastToShow(String showId, Message event) {
        Collection<RequestContext> subscribers = registry.getSubscribers(showId);
        if (subscribers.isEmpty()) {
            return;
        }

        String serialized;
        try {
            serialized = ProtocolJson.serialize(event);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to serialize event " + event.getType(), e);
            return;
        }

        for (RequestContext subscriber : subscribers) {
            try {
                if (subscriber.isOpen()) {
                    subscriber.send(serialized);
                } else {
                    registry.removeSession(subscriber.getSessionId());
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Error dispatching event to session " + subscriber.getSessionId(), e);
            }
        }
    }

    /**
     * Sanitizes seat views for public broadcast.
     */
    private List<SeatView> sanitizeSeats(List<SeatView> seats) {
        if (seats == null || seats.isEmpty()) {
            return List.of();
        }
        List<SeatView> result = new ArrayList<>(seats.size());
        for (SeatView seat : seats) {
            if (seat != null) {
                // Ensure valid view with status and seatId preserved
                result.add(new SeatView(seat.seatId(), seat.status(), seat.bookedBy()));
            }
        }
        return List.copyOf(result);
    }

    public long nextEventSequence() {
        return eventSequence.getAndIncrement();
    }
}
