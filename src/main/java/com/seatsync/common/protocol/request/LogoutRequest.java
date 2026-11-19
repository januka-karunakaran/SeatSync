package com.seatsync.common.protocol.request;

import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.RequestMessage;
import java.util.Objects;

/**
 * Request to terminate authenticated session.
 *
 * @param requestId Unique client request ID
 */
public record LogoutRequest(
    String requestId
) implements RequestMessage {
    public LogoutRequest {
        Objects.requireNonNull(requestId, "requestId must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.LOGOUT;
    }
}
