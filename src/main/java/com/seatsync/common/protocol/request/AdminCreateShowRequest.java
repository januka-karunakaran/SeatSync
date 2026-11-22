package com.seatsync.common.protocol.request;
import com.seatsync.common.model.ShowDetails;
import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.RequestMessage;
public record AdminCreateShowRequest(String requestId, String movieId, ShowDetails showDetails) implements RequestMessage {
    public AdminCreateShowRequest {
        java.util.Objects.requireNonNull(requestId); java.util.Objects.requireNonNull(movieId); java.util.Objects.requireNonNull(showDetails);
    }
    @Override public MessageType getType() { return MessageType.ADMIN_CREATE_SHOW; }
}
