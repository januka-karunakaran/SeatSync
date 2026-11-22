package com.seatsync.common.protocol.reply;

import com.seatsync.common.model.ShowSummary;
import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.ReplyMessage;
import java.util.List;
import java.util.Objects;

/**
 * Reply containing available shows in catalog.
 */
public record ShowListReply(
    String requestId,
    List<ShowSummary> shows
) implements ReplyMessage {
    public ShowListReply {
        Objects.requireNonNull(requestId, "requestId must not be null");
        shows = (shows == null) ? List.of() : List.copyOf(shows);
    }

    @Override
    public MessageType getType() {
        return MessageType.SHOW_LIST_REPLY;
    }
}
