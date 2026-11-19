package com.seatsync.common.protocol.request;

import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.RequestMessage;
import java.util.Objects;

/**
 * Client authentication request.
 *
 * @param requestId Unique client request ID
 * @param username  Client username
 * @param password  Client plaintext password for verification
 */
public record LoginRequest(
    String requestId,
    String username,
    String password
) implements RequestMessage {
    public LoginRequest {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(password, "password must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.LOGIN;
    }
}
