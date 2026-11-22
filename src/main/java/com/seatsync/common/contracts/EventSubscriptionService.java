package com.seatsync.common.contracts;

/**
 * Service contract for selective real-time event pub/sub subscription.
 * Allows clients to subscribe only to events relevant to shows they are viewing.
 */
public interface EventSubscriptionService {

    /**
     * Subscribes a client session to events for a specific show.
     *
     * @param sessionId Client connection session ID
     * @param showId    Target show ID
     */
    void subscribe(String sessionId, String showId);

    /**
     * Unsubscribes a client session from events for a show.
     *
     * @param sessionId Client connection session ID
     * @param showId    Target show ID
     */
    void unsubscribe(String sessionId, String showId);

    /**
     * Checks if a session is subscribed to a show.
     *
     * @param sessionId Client connection session ID
     * @param showId    Target show ID
     * @return True if subscribed
     */
    boolean isSubscribed(String sessionId, String showId);
}
