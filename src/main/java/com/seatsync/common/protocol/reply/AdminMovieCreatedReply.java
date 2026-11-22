package com.seatsync.common.protocol.reply;
import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.ReplyMessage;
public record AdminMovieCreatedReply(String requestId, String movieId, String message) implements ReplyMessage {
    @Override public MessageType getType() { return MessageType.ADMIN_MOVIE_CREATED; }
}
