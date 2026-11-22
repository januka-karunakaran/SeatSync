package com.seatsync.common.protocol.reply;
import com.seatsync.common.model.MovieDetails;
import com.seatsync.common.model.PagedResult;
import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.ReplyMessage;
public record MovieListReply(String requestId, PagedResult<MovieDetails> movies) implements ReplyMessage {
    @Override public MessageType getType() { return MessageType.MOVIE_LIST_REPLY; }
}
