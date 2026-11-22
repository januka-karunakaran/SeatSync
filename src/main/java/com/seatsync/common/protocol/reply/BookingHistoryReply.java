package com.seatsync.common.protocol.reply;

import com.seatsync.common.model.BookingHistoryItem;
import com.seatsync.common.model.PagedResult;
import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.ReplyMessage;
import java.util.Objects;

/**
 * Paginated booking history reply.
 */
public record BookingHistoryReply(
    String requestId,
    PagedResult<BookingHistoryItem> history
) implements ReplyMessage {
    public BookingHistoryReply {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(history, "history must not be null");
    }

    @Override
    public MessageType getType() {
        return MessageType.BOOKING_HISTORY_REPLY;
    }
}
