package com.seatsync.client;

import com.seatsync.common.protocol.Message;
import com.seatsync.common.protocol.ProtocolJson;
import com.seatsync.common.protocol.ReplyMessage;
import com.seatsync.common.protocol.event.SeatUpdatedEvent;
import com.seatsync.common.protocol.event.SeatHeldEvent;
import com.seatsync.common.protocol.event.SeatReleasedEvent;
import com.seatsync.common.protocol.event.ShowUpdatedEvent;
import com.seatsync.common.protocol.event.WaitlistPromotedEvent;
import com.seatsync.common.protocol.event.MetricsUpdatedEvent;
import com.seatsync.common.protocol.reply.BookOkReply;
import com.seatsync.common.protocol.reply.BookRejectedReply;
import com.seatsync.common.protocol.reply.CancelOkReply;
import com.seatsync.common.protocol.reply.CancelRejectedReply;
import com.seatsync.common.protocol.reply.ErrorReply;
import com.seatsync.common.protocol.reply.LoginFailedReply;
import com.seatsync.common.protocol.reply.LoginOkReply;
import com.seatsync.common.protocol.reply.LogoutOkReply;
import com.seatsync.common.protocol.reply.SeatSnapshotReply;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Handles incoming JSON messages received from the TCP stream, decodes them,
 * correlates replies with pending client requests, and notifies registered listeners.
 * <p>
 * Owned by Member 01 (TCP Socket Programming & Desktop Client).
 */
public class ClientMessageHandler {

    /**
     * Listener interface for UI and test components to receive protocol events.
     */
    public interface Listener {
        default void onConnected() {}
        default void onDisconnected(String reason) {}
        default void onConnectionError(Throwable cause) {}
        default void onLoginSuccess(LoginOkReply reply) {}
        default void onLoginFailed(LoginFailedReply reply) {}
        default void onSeatSnapshot(SeatSnapshotReply reply) {}
        default void onBookSuccess(BookOkReply reply) {}
        default void onBookRejected(BookRejectedReply reply) {}
        default void onCancelSuccess(CancelOkReply reply) {}
        default void onCancelRejected(CancelRejectedReply reply) {}
        default void onLogoutSuccess(LogoutOkReply reply) {}
        default void onWaitlistPromoted(WaitlistPromotedEvent event) {}
        default void onSeatHeld(SeatHeldEvent event) {}
        default void onSeatReleased(SeatReleasedEvent event) {}
        default void onShowUpdated(ShowUpdatedEvent event) {}
        default void onMetricsUpdated(MetricsUpdatedEvent event) {}
        default void onSeatUpdated(SeatUpdatedEvent event) {}
        default void onError(ErrorReply error) {}
        default void onMalformedMessage(String rawJson, String errorMessage) {}
    }

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Registers a listener for protocol and lifecycle events.
     *
     * @param listener Listener instance
     */
    public void addListener(Listener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Removes a previously registered listener.
     *
     * @param listener Listener instance
     */
    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    /**
     * Processes a single raw JSON line received over the TCP socket.
     * Correlates replies with pending request futures and notifies listeners.
     *
     * @param jsonLine        Raw UTF-8 JSON line from the server
     * @param pendingRequests Map of active request futures keyed by requestId
     */
    public void handleIncomingLine(String jsonLine, ConcurrentMap<String, CompletableFuture<ReplyMessage>> pendingRequests) {
        if (jsonLine == null || jsonLine.isBlank()) {
            return;
        }

        Message message;
        try {
            message = ProtocolJson.deserializeMessage(jsonLine.trim());
        } catch (Exception e) {
            notifyMalformed(jsonLine, e.getMessage());
            return;
        }

        if (message instanceof ReplyMessage reply) {
            String reqId = reply.requestId();
            if (pendingRequests != null && reqId != null) {
                CompletableFuture<ReplyMessage> future = pendingRequests.remove(reqId);
                if (future != null) {
                    future.complete(reply);
                }
            }
            dispatchReply(reply);
        } else if (message instanceof WaitlistPromotedEvent offer) {
            for (Listener listener : listeners) {
                try {
                    listener.onWaitlistPromoted(offer);
                } catch (Exception ignored) {
                }
            }
        } else if (message instanceof SeatHeldEvent event) {
            for (Listener listener : listeners) {
                try {
                    listener.onSeatHeld(event);
                } catch (Exception ignored) {
                }
            }
        } else if (message instanceof SeatReleasedEvent event) {
            for (Listener listener : listeners) {
                try {
                    listener.onSeatReleased(event);
                } catch (Exception ignored) {
                }
            }
        } else if (message instanceof ShowUpdatedEvent event) {
            for (Listener listener : listeners) {
                try {
                    listener.onShowUpdated(event);
                } catch (Exception ignored) {
                }
            }
        } else if (message instanceof MetricsUpdatedEvent event) {
            for (Listener listener : listeners) {
                try {
                    listener.onMetricsUpdated(event);
                } catch (Exception ignored) {
                }
            }
        } else if (message instanceof SeatUpdatedEvent event) {
            for (Listener listener : listeners) {
                try {
                    listener.onSeatUpdated(event);
                } catch (Exception ignored) {
                }
            }
        } else {
            notifyMalformed(jsonLine, "Unrecognized message structure: " + message.getClass().getSimpleName());
        }
    }

    private void dispatchReply(ReplyMessage reply) {
        for (Listener listener : listeners) {
            try {
                if (reply instanceof LoginOkReply r) {
                    listener.onLoginSuccess(r);
                } else if (reply instanceof LoginFailedReply r) {
                    listener.onLoginFailed(r);
                } else if (reply instanceof SeatSnapshotReply r) {
                    listener.onSeatSnapshot(r);
                } else if (reply instanceof BookOkReply r) {
                    listener.onBookSuccess(r);
                } else if (reply instanceof BookRejectedReply r) {
                    listener.onBookRejected(r);
                } else if (reply instanceof CancelOkReply r) {
                    listener.onCancelSuccess(r);
                } else if (reply instanceof CancelRejectedReply r) {
                    listener.onCancelRejected(r);
                } else if (reply instanceof LogoutOkReply r) {
                    listener.onLogoutSuccess(r);
                } else if (reply instanceof ErrorReply r) {
                    listener.onError(r);
                }
            } catch (Exception ignored) {
            }
        }
    }

    public void handleConnected() {
        for (Listener listener : listeners) {
            try {
                listener.onConnected();
            } catch (Exception ignored) {
            }
        }
    }

    public void handleDisconnected(String reason, ConcurrentMap<String, CompletableFuture<ReplyMessage>> pendingRequests) {
        if (pendingRequests != null && !pendingRequests.isEmpty()) {
            java.io.IOException disconnectException = new java.io.IOException("Connection closed: " + reason);
            pendingRequests.values().forEach(future -> future.completeExceptionally(disconnectException));
            pendingRequests.clear();
        }

        for (Listener listener : listeners) {
            try {
                listener.onDisconnected(reason);
            } catch (Exception ignored) {
            }
        }
    }

    public void handleConnectionError(Throwable cause) {
        for (Listener listener : listeners) {
            try {
                listener.onConnectionError(cause);
            } catch (Exception ignored) {
            }
        }
    }

    private void notifyMalformed(String rawJson, String errorMessage) {
        for (Listener listener : listeners) {
            try {
                listener.onMalformedMessage(rawJson, errorMessage);
            } catch (Exception ignored) {
            }
        }
    }
}
