package com.seatsync.server;

import com.seatsync.booking.InMemoryReservationService;
import com.seatsync.concurrency.BookingRequestProcessor;
import com.seatsync.common.protocol.ProtocolJson;
import com.seatsync.common.protocol.event.SeatUpdatedEvent;
import com.seatsync.network.NioBookingServer;
import com.seatsync.network.ServerConfig;
import com.seatsync.concurrency.WorkerRequestDispatcher;
import com.seatsync.security.AuthService;
import com.seatsync.security.AuthorizationService;
import com.seatsync.security.DemoUserRepository;
import com.seatsync.security.PasswordHasher;
import com.seatsync.security.SecurityRequestDispatcher;

import java.io.IOException;

/**
 * Main entry point for the SeatSync Java NIO Server.
 * <p>
 * Defaults to loopback host (127.0.0.1) and port 8080.
 * Host and port can be overridden via command-line arguments.
 */
public class SeatSyncServerMain {

    public static void main(String[] args) {
        String host = ServerConfig.DEFAULT_HOST;
        int port = ServerConfig.DEFAULT_PORT;

        if (args.length == 1) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                host = args[0];
            }
        } else if (args.length >= 2) {
            host = args[0];
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("[WARN] Invalid port '" + args[1] + "', defaulting to " + ServerConfig.DEFAULT_PORT);
                port = ServerConfig.DEFAULT_PORT;
            }
        }

        ServerConfig config = ServerConfig.of(host, port);
        String password = System.getenv("SEATSYNC_DEMO_PASSWORD");
        if (password == null || password.isEmpty()) {
            System.err.println("[FATAL] Set SEATSYNC_DEMO_PASSWORD for the local demo accounts.");
            System.exit(1);
            return;
        }
        PasswordHasher hasher = new PasswordHasher();
        AuthService auth = new AuthService(new DemoUserRepository(password.toCharArray(), hasher), hasher);
        var reservations = new InMemoryReservationService();
        var booking = new BookingRequestProcessor(auth, reservations);
        WorkerRequestDispatcher dispatcher = new WorkerRequestDispatcher(
            new SecurityRequestDispatcher(auth, new AuthorizationService(), booking));
        NioBookingServer server = new NioBookingServer(config, dispatcher);

        // Wire server session registry to broadcast real-time seat changes to all connected clients
        reservations.addOfferListener(offer -> {
            String line = ProtocolJson.serialize(offer);
            server.getSessionRegistry().getAll().forEach(session -> session.sendToUser(offer.promotedUserId(), line));
        });
        reservations.addSeatChangeListener((showId, version, seats) -> {
            String line = ProtocolJson.serialize(new SeatUpdatedEvent(showId, version, seats));
            server.getSessionRegistry().getAll().forEach(session -> {
                if (session.isOpen() && session.isAuthenticated()) {
                    session.send(line);
                }
            });
        });

        System.out.println("=================================================================");
        System.out.println("  SeatSync Non-Blocking Java NIO Server (Java 21)");
        System.out.println("  Author: Member 02 (Network & Cross-Module Server Integration)");
        System.out.println("=================================================================");
        System.out.println("[INFO] Host: " + config.host());
        System.out.println("[INFO] Port: " + config.port());
        System.out.println("[INFO] Max Frame Size: " + config.maxFrameBytes() + " bytes (16 KiB)");
        System.out.println("[INFO] Transport: Non-blocking ServerSocketChannel + Selector loop");

        var expiry = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            var thread = new Thread(r, "seatsync-hold-expiry");
            thread.setDaemon(true);
            return thread;
        });
        try {
            server.start();
            expiry.scheduleWithFixedDelay(reservations::expireHolds, 1, 1, java.util.concurrent.TimeUnit.SECONDS);
            System.out.println("[INFO] SeatSync NIO Server successfully listening on " + host + ":" + server.getPort());
        } catch (IOException e) {
            System.err.println("[FATAL] Failed to start SeatSync NIO server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[INFO] Shutdown hook triggered, stopping NIO server...");
            expiry.shutdownNow();
            server.stop();
            dispatcher.close();
            System.out.println("[INFO] SeatSync NIO Server stopped gracefully.");
        }, "seatsync-shutdown-hook"));

        // Keep main thread alive while server is running
        try {
            while (server.isRunning()) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
