package com.seatsync.delivery;

import com.seatsync.common.contracts.EventSubscriptionService;
import com.seatsync.common.contracts.RequestContext;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

/**
 * Thread-safe show-scoped subscription registry.
 * Tracks which client sessions are subscribed to events for each specific show.
 * <p>
 * Owned by Member 01 (Reliability, Events &amp; Asynchronous Messaging).
 */
public class ShowSubscriptionRegistry implements EventSubscriptionService {

    // showId -> Set of sessionIds
    private final ConcurrentMap<String, Set<String>> showSubscriptions = new ConcurrentHashMap<>();

    // sessionId -> Set of showIds
    private final ConcurrentMap<String, Set<String>> sessionSubscriptions = new ConcurrentHashMap<>();

    // sessionId -> RequestContext for event delivery
    private final ConcurrentMap<String, RequestContext> sessionContexts = new ConcurrentHashMap<>();

    /**
     * Registers or updates the active RequestContext for a session.
     *
     * @param context Active client request context
     */
    public void registerContext(RequestContext context) {
        if (context != null && context.getSessionId() != null) {
            sessionContexts.put(context.getSessionId(), context);
        }
    }

    @Override
    public void subscribe(String sessionId, String showId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(showId, "showId must not be null");

        showSubscriptions.computeIfAbsent(showId, k -> new CopyOnWriteArraySet<>()).add(sessionId);
        sessionSubscriptions.computeIfAbsent(sessionId, k -> new CopyOnWriteArraySet<>()).add(showId);
    }

    @Override
    public void unsubscribe(String sessionId, String showId) {
        if (sessionId == null || showId == null) {
            return;
        }

        Set<String> sessions = showSubscriptions.get(showId);
        if (sessions != null) {
            sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                showSubscriptions.remove(showId, Collections.emptySet());
            }
        }

        Set<String> shows = sessionSubscriptions.get(sessionId);
        if (shows != null) {
            shows.remove(showId);
            if (shows.isEmpty()) {
                sessionSubscriptions.remove(sessionId, Collections.emptySet());
            }
        }
    }

    @Override
    public boolean isSubscribed(String sessionId, String showId) {
        if (sessionId == null || showId == null) {
            return false;
        }
        Set<String> subscribers = showSubscriptions.get(showId);
        return subscribers != null && subscribers.contains(sessionId);
    }

    /**
     * Returns an unmodifiable snapshot of active, open RequestContexts subscribed to a show.
     * Automatically evicts any sessions that have been closed.
     *
     * @param showId Show identifier
     * @return Collection of subscribed active RequestContext instances
     */
    public Collection<RequestContext> getSubscribers(String showId) {
        if (showId == null) {
            return Collections.emptyList();
        }
        Set<String> sessionIds = showSubscriptions.get(showId);
        if (sessionIds == null || sessionIds.isEmpty()) {
            return Collections.emptyList();
        }

        return sessionIds.stream()
            .map(sessionContexts::get)
            .filter(Objects::nonNull)
            .filter(context -> {
                if (!context.isOpen()) {
                    removeSession(context.getSessionId());
                    return false;
                }
                return true;
            })
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Unregisters a disconnected session, removing all of its show subscriptions.
     *
     * @param sessionId Session identifier
     */
    public void removeSession(String sessionId) {
        if (sessionId == null) {
            return;
        }
        sessionContexts.remove(sessionId);
        Set<String> shows = sessionSubscriptions.remove(sessionId);
        if (shows != null) {
            for (String showId : shows) {
                Set<String> subscribers = showSubscriptions.get(showId);
                if (subscribers != null) {
                    subscribers.remove(sessionId);
                }
            }
        }
    }

    /**
     * Returns all show IDs that a specific session is subscribed to.
     *
     * @param sessionId Session identifier
     * @return Set of subscribed show IDs
     */
    public Set<String> getSubscribedShows(String sessionId) {
        if (sessionId == null) {
            return Collections.emptySet();
        }
        Set<String> shows = sessionSubscriptions.get(sessionId);
        return shows != null ? Collections.unmodifiableSet(shows) : Collections.emptySet();
    }

    /**
     * Returns the total count of active subscriptions across all shows.
     */
    public int totalSubscriptionsCount() {
        return showSubscriptions.values().stream().mapToInt(Set::size).sum();
    }

    /**
     * Clears all subscriptions and contexts.
     */
    public void clear() {
        showSubscriptions.clear();
        sessionSubscriptions.clear();
        sessionContexts.clear();
    }
}
