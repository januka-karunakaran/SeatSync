package com.seatsync.common.protocol.request;

import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.RequestMessage;
import java.util.Objects;

/**
 * Asks the server to check a ticket's verification hash.
 * Only the ticket owner or a staff account may ask.
 */
public record VerifyTicketRequest(
    String requestId,
    String ticketId,
    String verificationHash
) implements RequestMessage {
    public VerifyTicketRequest {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(ticketId, "ticketId must not be null");
        Objects.requireNonNull(verificationHash, "verificationHash must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.VERIFY_TICKET;
    }
}
