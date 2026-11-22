package com.seatsync.common.protocol.request;
import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.RequestMessage;
public record AdminListBookingsRequest(String requestId, String showId, int pageNumber, int pageSize) implements RequestMessage {
    public AdminListBookingsRequest { java.util.Objects.requireNonNull(requestId); java.util.Objects.requireNonNull(showId); }
    @Override public MessageType getType() { return MessageType.ADMIN_LIST_BOOKINGS; }
}
