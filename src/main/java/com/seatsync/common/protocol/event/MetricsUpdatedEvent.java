package com.seatsync.common.protocol.event;

import com.seatsync.common.model.ServerMetricSnapshot;
import com.seatsync.common.protocol.Message;
import com.seatsync.common.protocol.MessageType;
import java.util.Objects;

/**
 * Server-push event broadcast when telemetry metrics snapshot is refreshed.
 */
public record MetricsUpdatedEvent(
    ServerMetricSnapshot metrics,
    long timestamp
) implements Message {
    public MetricsUpdatedEvent {
        Objects.requireNonNull(metrics, "metrics must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.METRICS_UPDATED_EVENT;
    }
}
