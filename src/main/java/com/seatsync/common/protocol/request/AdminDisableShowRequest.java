package com.seatsync.common.protocol.request;
import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.RequestMessage;
public record AdminDisableShowRequest(String requestId, String showId) implements RequestMessage {
    public AdminDisableShowRequest { java.util.Objects.requireNonNull(requestId); java.util.Objects.requireNonNull(showId); }
    @Override public MessageType getType() { return MessageType.ADMIN_DISABLE_SHOW; }
}
