package com.seatsync.common.protocol.reply;
import com.seatsync.common.model.AdminBookingView;
import com.seatsync.common.model.PagedResult;
import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.ReplyMessage;
public record AdminBookingsReply(String requestId, PagedResult<AdminBookingView> bookings) implements ReplyMessage {
    @Override public MessageType getType() { return MessageType.ADMIN_BOOKINGS_REPLY; }
}
