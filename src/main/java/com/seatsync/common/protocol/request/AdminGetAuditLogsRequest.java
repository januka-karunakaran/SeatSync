package com.seatsync.common.protocol.request;

import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.RequestMessage;
import java.util.Objects;

/**
 * Administrative request to query system audit logs.
 */
public record AdminGetAuditLogsRequest(
    String requestId,
    int limit
) implements RequestMessage {
    public AdminGetAuditLogsRequest {
        Objects.requireNonNull(requestId, "requestId must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.ADMIN_GET_AUDIT_LOGS;
    }
}
