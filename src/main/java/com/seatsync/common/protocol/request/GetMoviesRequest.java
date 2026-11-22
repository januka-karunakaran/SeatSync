package com.seatsync.common.protocol.request;
import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.RequestMessage;
public record GetMoviesRequest(String requestId, int pageNumber, int pageSize) implements RequestMessage {
    public GetMoviesRequest { java.util.Objects.requireNonNull(requestId); }
    @Override public MessageType getType() { return MessageType.GET_MOVIES; }
}
