package com.seatsync.common.protocol.request;

import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.RequestMessage;
import java.util.Objects;

/**
 * Request to confirm an active hold and finalize ticket purchase.
 * Includes idempotencyKey to prevent duplicate booking transactions.
 */
public record ConfirmHoldRequest(
    String requestId,
    String holdId,
    String idempotencyKey
) implements RequestMessage {
    public ConfirmHoldRequest {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(holdId, "holdId must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.CONFIRM_HOLD;
    }
}
