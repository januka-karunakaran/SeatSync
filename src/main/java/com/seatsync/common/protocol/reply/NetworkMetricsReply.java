package com.seatsync.common.protocol.reply;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.seatsync.common.model.NetworkMetricsSnapshot;
import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.ReplyMessage;

import java.util.Objects;

/**
 * Reply containing the immutable NIO network metrics snapshot.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NetworkMetricsReply(
    @JsonProperty("requestId") String requestId,
    @JsonProperty("metrics") NetworkMetricsSnapshot metrics
) implements ReplyMessage {
    public NetworkMetricsReply {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(metrics, "metrics must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.NETWORK_METRICS_REPLY;
    }
}
