package com.seatsync.common.protocol.request;
import com.seatsync.common.model.MovieDetails;
import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.RequestMessage;
public record AdminCreateMovieRequest(String requestId, MovieDetails movie) implements RequestMessage {
    public AdminCreateMovieRequest { java.util.Objects.requireNonNull(requestId); java.util.Objects.requireNonNull(movie); }
    @Override public MessageType getType() { return MessageType.ADMIN_CREATE_MOVIE; }
}
