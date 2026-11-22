package com.seatsync.common.protocol.reply;

import com.seatsync.common.model.ShowDetails;
import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.ReplyMessage;
import java.util.Objects;

/**
 * Reply containing detailed show layout and metadata.
 */
public record ShowDetailsReply(
    String requestId,
    ShowDetails showDetails
) implements ReplyMessage {
    public ShowDetailsReply {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(showDetails, "showDetails must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.SHOW_DETAILS_REPLY;
    }
}
