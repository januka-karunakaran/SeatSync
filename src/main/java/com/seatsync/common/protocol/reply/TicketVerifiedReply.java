package com.seatsync.common.protocol.reply;

import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.ReplyMessage;
import java.util.Objects;

/**
 * Result of checking a ticket hash. {@code valid} is false when the hash does not match.
 */
public record TicketVerifiedReply(
    String requestId,
    String ticketId,
    boolean valid
) implements ReplyMessage {
    public TicketVerifiedReply {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(ticketId, "ticketId must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.TICKET_VERIFIED;
    }
}
