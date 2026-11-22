package com.seatsync.common.protocol.reply;

import com.seatsync.common.model.Ticket;
import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.ReplyMessage;
import java.util.Objects;

/**
 * Digital ticket retrieval reply.
 */
public record TicketReply(
    String requestId,
    Ticket ticket
) implements ReplyMessage {
    public TicketReply {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(ticket, "ticket must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.TICKET_REPLY;
    }
}
