package com.seatsync.network;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe registry tracking all active ClientSession instances connected to the NIO server.
 */
public class ClientSessionRegistry {

    private final ConcurrentMap<String, ClientSession> sessions = new ConcurrentHashMap<>();

    /**
     * Registers a newly accepted client session.
     *
     * @param session Connected client session
     */
    public void register(ClientSession session) {
        if (session != null) {
            sessions.put(session.getSessionId(), session);
        }
    }

    /**
     * Unregisters a disconnected or closed client session.
     *
     * @param sessionId Session identifier
     * @return The removed ClientSession, or null if not found
     */
    public ClientSession unregister(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        return sessions.remove(sessionId);
    }

    /**
     * Retrieves a session by its unique ID.
     *
     * @param sessionId Session identifier
     * @return ClientSession or null if not found
     */
    public ClientSession get(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        return sessions.get(sessionId);
    }

    /**
     * Returns an unmodifiable snapshot of all currently active sessions.
     *
     * @return Collection of active ClientSession instances
     */
    public Collection<ClientSession> getAll() {
        return Collections.unmodifiableCollection(sessions.values());
    }

    /**
     * Returns an unmodifiable list of active session IDs.
     *
     * @return List of session IDs
     */
    public List<String> getSessionIds() {
        return List.copyOf(sessions.keySet());
    }

    /**
     * @return Current number of connected active sessions
     */
    public int size() {
        return sessions.size();
    }

    /**
     * Broadcasts a UTF-8 JSON line to all connected active sessions.
     * Outbound queuing is guaranteed thread-safe.
     *
     * @param jsonLine Line of JSON to transmit
     */
    public void broadcast(String jsonLine) {
        for (ClientSession session : sessions.values()) {
            if (session.isOpen()) {
                session.send(jsonLine);
            }
        }
    }

    /**
     * Closes and clears all active sessions.
     */
    public void closeAll() {
        for (ClientSession session : sessions.values()) {
            try {
                session.close();
            } catch (Exception ignored) {
            }
        }
        sessions.clear();
    }
}
