package com.seatsync.common.protocol.reply;
import com.seatsync.common.protocol.MessageType;
import com.seatsync.common.protocol.ReplyMessage;
public record AdminShowDisabledReply(String requestId, String showId, String message) implements ReplyMessage {
    @Override public MessageType getType() { return MessageType.ADMIN_SHOW_DISABLED; }
}
