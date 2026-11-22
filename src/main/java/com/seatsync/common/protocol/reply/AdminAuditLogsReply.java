package com.seatsync.common.protocol.reply;

import com.seatsync.common.model.AuditLogEntry;
import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.ReplyMessage;
import java.util.List;
import java.util.Objects;

/**
 * Administrative reply containing system audit log records.
 */
public record AdminAuditLogsReply(
    String requestId,
    List<AuditLogEntry> logs
) implements ReplyMessage {
    public AdminAuditLogsReply {
        Objects.requireNonNull(requestId, "requestId must not be null");
        logs = (logs == null) ? List.of() : List.copyOf(logs);
    }

    @Override
    public MessageType getType() {
        return MessageType.ADMIN_AUDIT_LOGS_REPLY;
    }
}
