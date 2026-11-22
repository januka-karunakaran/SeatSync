package com.seatsync.common.protocol.event;

import com.seatsync.common.protocol.Message;
import com.seatsync.common.protocol.MessageType;
import java.util.Objects;

/**
 * Server-push event notify when a waitlisted user is promoted to a newly available seat hold.
 */
public record WaitlistPromotedEvent(
    String showId,
    String seatId,
    String promotedUserId,
    String holdId,
    long expiresAt,
    long version
) implements Message {
    public WaitlistPromotedEvent {
        Objects.requireNonNull(showId, "showId must not be null");
        Objects.requireNonNull(seatId, "seatId must not be null");
        Objects.requireNonNull(promotedUserId, "promotedUserId must not be null");
        Objects.requireNonNull(holdId, "holdId must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.WAITLIST_PROMOTED_EVENT;
    }
}
