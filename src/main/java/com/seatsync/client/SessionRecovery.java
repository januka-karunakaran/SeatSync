package com.seatsync.client;

import com.seatsync.common.protocol.ReplyMessage;
import com.seatsync.common.protocol.reply.LoginOkReply;
import com.seatsync.common.protocol.reply.SubscribeOkReply;
import com.seatsync.common.protocol.request.LoginRequest;
import com.seatsync.common.protocol.request.SubscribeEventsRequest;
import com.seatsync.reliability.ReconnectCoordinator;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Opens a new connection after a drop, signs in again, and restores show subscriptions.
 * A failed secure connection is never retried as plain text; {@link TcpClient} already keeps that choice.
 */
public final class SessionRecovery {

    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    public boolean recover(TcpClient client, ReconnectCoordinator coordinator, String username, String password) {
        return recover(client, coordinator, username, password, Thread::sleep);
    }

    public boolean recover(TcpClient client, ReconnectCoordinator coordinator, String username, String password,
            Sleeper sleeper) {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(coordinator, "coordinator");
        Objects.requireNonNull(sleeper, "sleeper");
        if (username == null || username.isBlank() || password == null) {
            return false;
        }
        for (int attempt = 0; attempt < coordinator.getMaxRetries(); attempt++) {
            try {
                sleeper.sleep(coordinator.calculateBackoff(attempt));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
            try {
                client.connect(3000);
                ReplyMessage login = client.sendRequest(
                    new LoginRequest(client.nextRequestId(), username, password)).get(5, TimeUnit.SECONDS);
                if (!(login instanceof LoginOkReply)) {
                    client.disconnect();
                    continue;
                }
                boolean subscriptionsOk = true;
                for (String showId : coordinator.getTrackedSubscriptions()) {
                    ReplyMessage subscribed = client.sendRequest(
                        new SubscribeEventsRequest(client.nextRequestId(), showId)).get(5, TimeUnit.SECONDS);
                    if (!(subscribed instanceof SubscribeOkReply)) {
                        subscriptionsOk = false;
                        break;
                    }
                }
                if (!subscriptionsOk) {
                    client.disconnect();
                    continue;
                }
                return true;
            } catch (Exception failure) {
                try {
                    client.disconnect();
                } catch (RuntimeException ignored) {
                    // The socket is already closed.
                }
            }
        }
        return false;
    }
}
