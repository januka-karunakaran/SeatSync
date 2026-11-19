package com.seatsync.common.protocol;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.seatsync.common.protocol.event.SeatUpdatedEvent;
import com.seatsync.common.protocol.reply.BookOkReply;
import com.seatsync.common.protocol.reply.BookRejectedReply;
import com.seatsync.common.protocol.reply.CancelOkReply;
import com.seatsync.common.protocol.reply.CancelRejectedReply;
import com.seatsync.common.protocol.reply.ErrorReply;
import com.seatsync.common.protocol.reply.LoginFailedReply;
import com.seatsync.common.protocol.reply.LoginOkReply;
import com.seatsync.common.protocol.reply.LogoutOkReply;
import com.seatsync.common.protocol.reply.SeatSnapshotReply;
import com.seatsync.common.protocol.request.BookSeatRequest;
import com.seatsync.common.protocol.request.CancelBookingRequest;
import com.seatsync.common.protocol.request.GetSeatsRequest;
import com.seatsync.common.protocol.request.LoginRequest;
import com.seatsync.common.protocol.request.LogoutRequest;

/**
 * Root interface for all protocol messages exchanged over the TCP wire.
 * Automatically handles Jackson polymorphic type discrimination via the "type" property.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
    visible = true
)
@JsonSubTypes({
    // Requests
    @JsonSubTypes.Type(value = LoginRequest.class, name = "LOGIN"),
    @JsonSubTypes.Type(value = GetSeatsRequest.class, name = "GET_SEATS"),
    @JsonSubTypes.Type(value = BookSeatRequest.class, name = "BOOK_SEAT"),
    @JsonSubTypes.Type(value = CancelBookingRequest.class, name = "CANCEL_BOOKING"),
    @JsonSubTypes.Type(value = LogoutRequest.class, name = "LOGOUT"),

    // Replies
    @JsonSubTypes.Type(value = LoginOkReply.class, name = "LOGIN_OK"),
    @JsonSubTypes.Type(value = LoginFailedReply.class, name = "LOGIN_FAILED"),
    @JsonSubTypes.Type(value = SeatSnapshotReply.class, name = "SEAT_SNAPSHOT"),
    @JsonSubTypes.Type(value = BookOkReply.class, name = "BOOK_OK"),
    @JsonSubTypes.Type(value = BookRejectedReply.class, name = "BOOK_REJECTED"),
    @JsonSubTypes.Type(value = CancelOkReply.class, name = "CANCEL_OK"),
    @JsonSubTypes.Type(value = CancelRejectedReply.class, name = "CANCEL_REJECTED"),
    @JsonSubTypes.Type(value = LogoutOkReply.class, name = "LOGOUT_OK"),
    @JsonSubTypes.Type(value = ErrorReply.class, name = "ERROR"),

    // Server Events
    @JsonSubTypes.Type(value = SeatUpdatedEvent.class, name = "SEAT_UPDATED")
})
public interface Message {
    /**
     * @return Message type identifier
     */
    MessageType getType();
}
