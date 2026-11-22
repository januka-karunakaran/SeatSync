package com.seatsync.common.protocol.request;

import com.seatsync.common.model.ShowDetails;
import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.RequestMessage;
import java.util.Objects;

/**
 * Administrative request to add a new show to the catalog.
 */
public record AdminAddShowRequest(
    String requestId,
    ShowDetails showDetails
) implements RequestMessage {
    public AdminAddShowRequest {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(showDetails, "showDetails must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.ADMIN_ADD_SHOW;
    }
}
