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
    long version
) implements Message {
    public ShowUpdatedEvent {
        Objects.requireNonNull(showId, "showId must not be null");
        Objects.requireNonNull(summary, "summary must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.SHOW_UPDATED_EVENT;
    }
}
