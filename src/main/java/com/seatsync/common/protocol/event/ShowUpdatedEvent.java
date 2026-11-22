package com.seatsync.common.protocol.event;

import com.seatsync.common.model.ShowSummary;
import com.seatsync.common.protocol.Message;
import com.seatsync.common.protocol.MessageType;
import java.util.Objects;

/**
 * Server-push event broadcast when catalog show availability or metadata changes.
 */
public record ShowUpdatedEvent(
    String showId,
    ShowSummary summary,
    long version,
    Boolean enabled
) implements Message {
    public ShowUpdatedEvent {
        Objects.requireNonNull(showId, "showId must not be null");
        Objects.requireNonNull(summary, "summary must not be null");
        enabled = enabled == null ? Boolean.TRUE : enabled;
    }

    public ShowUpdatedEvent(String showId, ShowSummary summary, long version) {
        this(showId, summary, version, true);
    }

    @Override
    public MessageType getType() {
        return MessageType.SHOW_UPDATED_EVENT;
    }
}
