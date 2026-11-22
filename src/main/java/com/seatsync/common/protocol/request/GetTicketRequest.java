package com.seatsync.common.protocol.request;

import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.RequestMessage;
import java.util.Objects;

/**
 * Request to retrieve a digital ticket by ticketId.
 */
public record GetTicketRequest(
    String requestId,
    String ticketId
) implements RequestMessage {
    public GetTicketRequest {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(ticketId, "ticketId must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.GET_TICKET;
    }
}
