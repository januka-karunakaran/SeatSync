package com.seatsync.common.protocol;

/**
 * Marker interface for all server-to-client reply messages.
 * Replies echo the correlation requestId from the corresponding request.
 */
public interface ReplyMessage extends Message {

    /**
     * @return Correlated request identifier
     */
    String requestId();

    /**
     * JavaBean-style getter alias delegating to {@link #requestId()}.
     *
     * @return Correlated request identifier
     */
    default String getRequestId() {
        return requestId();
    }
}
