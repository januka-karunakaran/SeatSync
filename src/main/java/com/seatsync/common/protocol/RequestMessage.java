package com.seatsync.common.protocol;

/**
 * Marker interface for all client-initiated request messages.
 * All requests must include a client-supplied requestId for correlation.
 */
public interface RequestMessage extends Message {

    /**
     * @return Client-supplied correlation request identifier
     */
    String requestId();

    /**
     * JavaBean-style getter alias delegating to {@link #requestId()}.
     *
     * @return Client-supplied correlation request identifier
     */
    default String getRequestId() {
        return requestId();
    }
}
