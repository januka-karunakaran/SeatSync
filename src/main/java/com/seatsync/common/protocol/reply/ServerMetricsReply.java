package com.seatsync.common.protocol.reply;

import com.seatsync.common.model.ServerMetricSnapshot;
import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.ReplyMessage;
import java.util.Objects;

/**
 * Server telemetry metrics snapshot reply.
 */
public record ServerMetricsReply(
    String requestId,
    ServerMetricSnapshot metrics
) implements ReplyMessage {
    public ServerMetricsReply {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(metrics, "metrics must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.SERVER_METRICS_REPLY;
    }
}
