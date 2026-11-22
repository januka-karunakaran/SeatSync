package com.seatsync.network;

import com.seatsync.common.contracts.RequestDispatcher;
import com.seatsync.common.model.TlsConfigMode;
import com.seatsync.network.discovery.LanDiscoveryListener;
import com.seatsync.network.metrics.NetworkMetricsTracker;
import com.seatsync.network.tls.TlsHandler;

import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * High-performance, non-blocking Java NIO TCP Server for SeatSync.
 * Uses a single selector thread to handle OP_ACCEPT, OP_READ, and OP_WRITE,
 * ensuring high throughput without allocating a thread per connection.
 */
public class NioBookingServer {

    private final ServerConfig config;
    private final NetworkMetricsTracker metricsTracker;
    private final ClientSessionRegistry sessionRegistry;
    private final ConcurrentLinkedQueue<ClientSession> pendingWriteSessions = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private enum PortType { PLAINTEXT, TLS }

    private ServerSocketChannel serverSocketChannel;
    private ServerSocketChannel secureServerSocketChannel;
    private Selector selector;
    private Thread selectorThread;
    private volatile RequestDispatcher requestDispatcher;
    private LanDiscoveryListener discoveryListener;

    public NioBookingServer(ServerConfig config, RequestDispatcher requestDispatcher, NetworkMetricsTracker metricsTracker) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.requestDispatcher = requestDispatcher;
        this.metricsTracker = metricsTracker != null ? metricsTracker : new NetworkMetricsTracker();
        this.sessionRegistry = new ClientSessionRegistry(this.metricsTracker);
    }

    public NioBookingServer(ServerConfig config, RequestDispatcher requestDispatcher) {
        this(config, requestDispatcher, null);
    }

    public NioBookingServer(ServerConfig config) {
        this(config, null);
    }

    public NioBookingServer(int port) {
        this(ServerConfig.of(ServerConfig.DEFAULT_HOST, port), null);
    }

    public NioBookingServer() {
        this(ServerConfig.defaultConfig(), null);
    }

    public synchronized void setRequestDispatcher(RequestDispatcher requestDispatcher) {
        this.requestDispatcher = requestDispatcher;
    }

    public RequestDispatcher getRequestDispatcher() {
        return requestDispatcher;
    }

    public ClientSessionRegistry getSessionRegistry() {
        return sessionRegistry;
    }

    public NetworkMetricsTracker getMetricsTracker() {
        return metricsTracker;
    }

    public LanDiscoveryListener getDiscoveryListener() {
        return discoveryListener;
    }

    public ServerConfig getConfig() {
        return config;
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * Starts the NIO server and launches the single selector thread loop.
     *
     * @throws IOException if binding or selector initialization fails
     */
    public synchronized void start() throws IOException {
        if (running.get()) {
            return;
        }

        selector = Selector.open();

        if (config.tlsMode() != TlsConfigMode.REQUIRED) {
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.bind(new InetSocketAddress(config.host(), config.port()));
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT, PortType.PLAINTEXT);
        }

        if (config.tlsMode() != TlsConfigMode.DISABLED && config.sslContext() != null) {
            secureServerSocketChannel = ServerSocketChannel.open();
            secureServerSocketChannel.configureBlocking(false);
            secureServerSocketChannel.bind(new InetSocketAddress(config.host(), config.tlsPort()));
            secureServerSocketChannel.register(selector, SelectionKey.OP_ACCEPT, PortType.TLS);
        }

        running.set(true);
        selectorThread = new Thread(this::runSelectorLoop, "seatsync-nio-selector");
        selectorThread.setDaemon(true);
        selectorThread.start();

        if (config.udpDiscoveryEnabled()) {
            discoveryListener = new LanDiscoveryListener(
                config.udpPort(),
                config.manualLanIp(),
                getPort(),
                false,
                0,
                "seatsync-v2",
                null
            );
            discoveryListener.start();
        }
    }

    /**
     * Gracefully stops the NIO server, unregisters selector keys, and closes all client connections.
     */
    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        if (selector != null) {
            selector.wakeup();
        }

        sessionRegistry.closeAll();

        if (discoveryListener != null) {
            discoveryListener.close();
            discoveryListener = null;
        }

        try {
            if (serverSocketChannel != null && serverSocketChannel.isOpen()) {
                serverSocketChannel.close();
            }
        } catch (IOException ignored) {
        }

        try {
            if (secureServerSocketChannel != null && secureServerSocketChannel.isOpen()) {
                secureServerSocketChannel.close();
            }
        } catch (IOException ignored) {
        }

        try {
            if (selector != null && selector.isOpen()) {
                selector.close();
            }
        } catch (IOException ignored) {
        }

        if (selectorThread != null && Thread.currentThread() != selectorThread) {
            try {
                selectorThread.join(2000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Wakes up the selector thread to process newly enqueued outbound data for a session.
     *
     * @param session ClientSession with pending outbound frames
     */
    public void scheduleWrite(ClientSession session) {
        if (session != null && running.get()) {
            pendingWriteSessions.offer(session);
            if (selector != null && selector.isOpen()) {
                selector.wakeup();
            }
        }
    }

    /**
     * Retrieves the actual port bound by this server (useful when testing with port 0).
     *
     * @return Local port number
     */
    public int getPort() {
        if (serverSocketChannel != null && serverSocketChannel.isOpen()) {
            try {
                return ((InetSocketAddress) serverSocketChannel.getLocalAddress()).getPort();
            } catch (IOException ignored) {
            }
        }
        return config.port();
    }

    /**
     * Retrieves the actual TLS port bound by this server (useful when testing with port 0).
     *
     * @return Local secure TLS port number
     */
    public int getTlsPort() {
        if (secureServerSocketChannel != null && secureServerSocketChannel.isOpen()) {
            try {
                return ((InetSocketAddress) secureServerSocketChannel.getLocalAddress()).getPort();
            } catch (IOException ignored) {
            }
        }
        return config.tlsPort();
    }

    /**
     * Main single-threaded event loop processing NIO selector readiness.
     */
    private void runSelectorLoop() {
        while (running.get()) {
            try {
                processPendingWrites();

                int selected = selector.select(500);

                processPendingWrites();

                if (!running.get()) {
                    break;
                }

                if (selected == 0) {
                    continue;
                }

                Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    if (key.isAcceptable()) {
                        handleAccept(key);
                    }

                    if (key.isValid() && key.isReadable()) {
                        handleRead(key);
                    }

                    if (key.isValid() && key.isWritable()) {
                        handleWrite(key);
                    }
                }
            } catch (ClosedSelectorException e) {
                break;
            } catch (Exception e) {
                if (!running.get()) {
                    break;
                }
            }
        }
    }

    private void processPendingWrites() {
        ClientSession session;
        while ((session = pendingWriteSessions.poll()) != null) {
            if (session.isOpen()) {
                session.enableWriteInterest();
                try {
                    session.handleWrite();
                } catch (IOException e) {
                    session.close();
                }
            }
        }
    }

    private void handleAccept(SelectionKey key) throws IOException {
        PortType type = (PortType) key.attachment();
        if (type == PortType.TLS) {
            handleSecureAccept();
        } else {
            handlePlainAccept();
        }
    }

    private void handlePlainAccept() throws IOException {
        if (serverSocketChannel == null) {
            return;
        }
        SocketChannel clientChannel = serverSocketChannel.accept();
        if (clientChannel == null) {
            return;
        }

        metricsTracker.recordAcceptedConnection();
        clientChannel.configureBlocking(false);
        ClientSession session = new ClientSession(
            clientChannel,
            this,
            config.maxFrameBytes(),
            config.readBufferBytes(),
            metricsTracker
        );

        SelectionKey clientKey = clientChannel.register(selector, SelectionKey.OP_READ, session);
        session.setSelectionKey(clientKey);
        sessionRegistry.register(session);
    }

    private void handleSecureAccept() throws IOException {
        if (secureServerSocketChannel == null) {
            return;
        }
        SocketChannel clientChannel = secureServerSocketChannel.accept();
        if (clientChannel == null) {
            return;
        }

        metricsTracker.recordAcceptedConnection();
        clientChannel.configureBlocking(false);

        SSLEngine engine = com.seatsync.security.tls.TlsContextFactory.serverEngine(config.sslContext());
        ClientSession session = new ClientSession(
            clientChannel,
            this,
            config.maxFrameBytes(),
            config.readBufferBytes(),
            metricsTracker
        );
        TlsHandler tlsHandler = new TlsHandler(clientChannel, engine, session, this);
        session.setTlsHandler(tlsHandler);

        SelectionKey clientKey = clientChannel.register(selector, SelectionKey.OP_READ, session);
        session.setSelectionKey(clientKey);
        sessionRegistry.register(session);
        tlsHandler.startHandshake();
    }

    private void handleRead(SelectionKey key) {
        ClientSession session = (ClientSession) key.attachment();
        if (session != null) {
            try {
                session.handleRead(requestDispatcher);
            } catch (IOException e) {
                session.close();
            }
        }
    }

    private void handleWrite(SelectionKey key) {
        ClientSession session = (ClientSession) key.attachment();
        if (session != null) {
            try {
                session.handleWrite();
            } catch (IOException e) {
                session.close();
            }
        }
    }
}
