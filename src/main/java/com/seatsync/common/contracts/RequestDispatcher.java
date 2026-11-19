package com.seatsync.common.contracts;

/**
 * Dispatches incoming raw client message frames to appropriate business service handlers.
 */
public interface RequestDispatcher {

    /**
     * Dispatches an incoming JSON frame received from a client connection.
     *
     * @param context  Session context of the sending connection
     * @param jsonLine Raw JSON string frame received from wire
     */
    void dispatch(RequestContext context, String jsonLine);
}
