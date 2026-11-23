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
import com.seatsync.security.AdminRequestDispatcher;
import com.seatsync.security.AdminService;
import com.seatsync.security.SecurityAuditLog;
import com.seatsync.security.HoldAdminBackend;
import com.seatsync.security.tls.TlsServerConfiguration;
import com.seatsync.common.model.TlsConfigMode;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicReference;

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

        ServerConfig config;
        try {
            config = TlsServerConfiguration.fromEnvironment(host, port, System.getenv());
        } catch (IOException | java.security.GeneralSecurityException | IllegalArgumentException invalid) {
            System.err.println("[FATAL] Invalid TLS configuration; check mode, paths, passwords and certificate validity.");
            System.exit(1);
            return;
        }
        String password = System.getenv("SEATSYNC_DEMO_PASSWORD");
        if (password == null || password.isEmpty()) {
            System.err.println("[FATAL] Set SEATSYNC_DEMO_PASSWORD for the local demo accounts.");
            System.exit(1);
            return;
        }
        PasswordHasher hasher = new PasswordHasher();
        String adminPassword = System.getenv("SEATSYNC_ADMIN_PASSWORD");
        DemoUserRepository users = new DemoUserRepository(password.toCharArray(),
            adminPassword == null ? null : adminPassword.toCharArray(), hasher);
        AuthService auth = new AuthService(users, hasher);
        var authorization = new AuthorizationService(users);
        var audit = new SecurityAuditLog();
        var reservations = new InMemoryReservationService();
        var subscriptionRegistry = new com.seatsync.delivery.ShowSubscriptionRegistry();
        var showEventBus = new com.seatsync.delivery.ShowEventBus(subscriptionRegistry);
        var ticketManager = new com.seatsync.tickets.TicketManager();
        var booking = new BookingRequestProcessor(auth, reservations, subscriptionRegistry, ticketManager, ticketManager);
        var serverReference = new AtomicReference<NioBookingServer>();
        var adminService = new AdminService(authorization, reservations, reservations::registerShow,
            new HoldAdminBackend(authorization, reservations), audit, Clock.systemUTC(), show -> {
                audit.record("system", "SHOW_CATALOG_CHANGED", show.showId(), SecurityAuditLog.Outcome.SUCCESS);
                int capacity = show.layout() != null ? show.layout().totalCapacity() : 40;
                var summary = new com.seatsync.common.model.ShowSummary(
                    show.showId(), show.title(), show.startTime(), show.venue(), capacity, capacity);
                showEventBus.publishShowUpdated(show.showId(), summary, 1L, true);
            });
        var adminDispatcher = new AdminRequestDispatcher(authorization, adminService, audit,
            () -> serverReference.get().getMetricsTracker().getNetworkMetricsSnapshot(),
            () -> serverReference.get().getMetricsTracker().getMetricsSnapshot(), booking);
        var idempotencyCache = new com.seatsync.reliability.IdempotencyCache();
        var secureDispatcher = new SecurityRequestDispatcher(auth, authorization, adminDispatcher, audit);
        var idempotentDispatcher = new com.seatsync.reliability.IdempotentRequestDispatcher(secureDispatcher, idempotencyCache);
        WorkerRequestDispatcher dispatcher = new WorkerRequestDispatcher(idempotentDispatcher);
        NioBookingServer server = new NioBookingServer(config, dispatcher);
        serverReference.set(server);
        reservations.addCatalogChangeListener(event -> {
            String line = ProtocolJson.serialize(event);
            server.getSessionRegistry().getAll().forEach(session -> {
                if (session.isOpen() && session.isAuthenticated()) { session.send(line); }
            });
        });

        // Wire server session registry to broadcast real-time seat changes to all connected clients
        reservations.addOfferListener(offer -> {
            String line = ProtocolJson.serialize(offer);
            server.getSessionRegistry().getAll().forEach(session -> session.sendToUser(offer.promotedUserId(), line));
        });
        reservations.addSeatChangeListener((showId, version, seats) -> {
            showEventBus.publishSeatUpdated(showId, version, seats);
        });

        System.out.println("=================================================================");
        System.out.println("  SeatSync Non-Blocking Java NIO Server (Java 21)");
        System.out.println("  Author: Member 02 (Network & Cross-Module Server Integration)");
        System.out.println("=================================================================");
        System.out.println("[INFO] Host: " + config.host());
        System.out.println("[INFO] Port: " + config.port());
        System.out.println("[INFO] Max Frame Size: " + config.maxFrameBytes() + " bytes (16 KiB)");
        System.out.println("[INFO] Transport: Non-blocking ServerSocketChannel + Selector loop");
        System.out.println(config.tlsMode() == TlsConfigMode.REQUIRED
            ? "[INFO] TLS 1.3 required; plaintext listener disabled. Secure port: " + config.tlsPort()
            : "[INFO] PLAINTEXT LOCAL DEVELOPMENT ONLY (loopback); traffic is not encrypted.");

        var expiry = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            var thread = new Thread(r, "seatsync-hold-expiry");
            thread.setDaemon(true);
            return thread;
        });
        try {
            server.start();
            expiry.scheduleWithFixedDelay(reservations::expireHolds, 1, 1, java.util.concurrent.TimeUnit.SECONDS);
            int listeningPort = config.tlsMode() == TlsConfigMode.REQUIRED ? server.getTlsPort() : server.getPort();
            System.out.println("[INFO] SeatSync NIO Server successfully listening on " + host + ":" + listeningPort);
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
