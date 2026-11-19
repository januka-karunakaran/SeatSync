package com.seatsync.common.contracts;

import java.util.Optional;

/**
 * Minimal session abstraction representing an active client connection on the server.
 * Encapsulates session identity and guarantees thread-safe outbound frame transmission.
 */
public interface RequestContext {

    /**
     * @return Unique session/connection identifier assigned by the server
     */
    String getSessionId();

    /**
     * @return Authenticated user ID bound to this session, or empty if unauthenticated
     */
    Optional<String> getAuthenticatedUserId();

    /**
     * Checks whether this session has been authenticated.
     *
     * @return true if an authenticated user ID is present
     */
    default boolean isAuthenticated() {
        return getAuthenticatedUserId().isPresent();
    }

    /**
     * Associates an authenticated user identity with this session.
     *
     * @param userId The server-assigned user ID
     */
    void setAuthenticatedUserId(String userId);

    /**
     * Clears user identity from the session upon logout.
     */
    void clearAuthentication();

    /**
     * Thread-safe outbound send API.
     * Sends an outbound frame over the connection. Implementation MUST guarantee
     * thread-safety so that worker threads and asynchronous broadcast threads can
     * call this method concurrently without corrupting network frames.
     *
     * @param jsonLine JSON message line (implementation ensures newline termination if omitted)
     */
    void send(String jsonLine);

    /**
     * Checks if the underlying connection is still open and active.
     *
     * @return true if open
     */
    boolean isOpen();

    /**
     * Closes the client connection and releases any associated session resources.
     */
    void close();
}
