package com.seatsync.network.tls;

import com.seatsync.network.ClientSession;
import com.seatsync.network.NioBookingServer;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * High-performance, non-blocking Java 21 {@link SSLEngine} transport handler for NIO SocketChannels.
 * <p>
 * Handles all TLS 1.3 handshake statuses (NEED_WRAP, NEED_UNWRAP, NEED_UNWRAP_AGAIN, NEED_TASK, FINISHED)
 * without blocking the single selector thread.
 * <p>
 * Manages separate encrypted-network and plaintext-application ByteBuffers, partial record framing,
 * backpressure via OP_WRITE interest, and graceful TLS close alerts.
 */
public class TlsHandler {

    private static final ExecutorService TASK_EXECUTOR = Executors.newFixedThreadPool(
        Math.max(2, Runtime.getRuntime().availableProcessors()),
        r -> {
            Thread t = new Thread(r, "seatsync-tls-worker");
            t.setDaemon(true);
            return t;
        }
    );

    private final SocketChannel channel;
    private final SSLEngine engine;
    private final ClientSession session;
    private final NioBookingServer server;

    private ByteBuffer netIn;
    private ByteBuffer appIn;
    private ByteBuffer netOut;
    private final ByteBuffer emptyApp = ByteBuffer.allocate(0);

    private final ConcurrentLinkedQueue<ByteBuffer> outboundPlaintextQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean handshaking = new AtomicBoolean(true);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private boolean netOutPending = false;
    private ByteBuffer currentPlaintextWrite = null;

    public TlsHandler(
        SocketChannel channel,
        SSLEngine engine,
        ClientSession session,
        NioBookingServer server
    ) {
        this.channel = Objects.requireNonNull(channel, "channel must not be null");
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
        this.session = Objects.requireNonNull(session, "session must not be null");
        this.server = Objects.requireNonNull(server, "server must not be null");

        SSLSession sslSession = engine.getSession();
        int appBufferSize = Math.max(sslSession.getApplicationBufferSize(), 32 * 1024);
        int packetBufferSize = Math.max(sslSession.getPacketBufferSize(), 32 * 1024);

        this.netIn = ByteBuffer.allocate(packetBufferSize);
        this.appIn = ByteBuffer.allocate(appBufferSize);
        this.netOut = ByteBuffer.allocate(packetBufferSize);

        // Server-mode TLS
        this.engine.setUseClientMode(false);
        this.engine.setNeedClientAuth(false);
        this.engine.setWantClientAuth(false);
    }

    public SSLEngine getEngine() {
        return engine;
    }

    public boolean isHandshakeComplete() {
        return !handshaking.get();
    }

    /**
     * Initiates the server TLS handshake and processes initial status.
     */
    public synchronized void startHandshake() throws IOException {
        engine.beginHandshake();
        processHandshake();
    }

    /**
     * Flushes any encrypted bytes currently in netOut to the channel.
     *
     * @return true if netOut is completely drained; false if partial write occurred
     */
    private boolean flushNetOut() throws IOException {
        if (!netOutPending) {
            return true;
        }

        while (netOut.hasRemaining()) {
            int written = channel.write(netOut);
            if (written == 0) {
                session.enableWriteInterest();
                return false;
            }
        }

        netOut.clear();
        netOutPending = false;
        session.disableWriteInterest();
        return true;
    }

    /**
     * Main non-blocking TLS handshake driver loop.
     */
    public synchronized boolean processHandshake() throws IOException {
        if (!handshaking.get()) {
            return true;
        }

        if (!flushNetOut()) {
            return false;
        }

        while (handshaking.get()) {
            HandshakeStatus hs = engine.getHandshakeStatus();
            switch (hs) {
                case NEED_WRAP -> {
                    netOut.clear();
                    SSLEngineResult result;
                    try {
                        result = engine.wrap(emptyApp, netOut);
                    } catch (SSLException e) {
                        close();
                        session.close();
                        return false;
                    }
                    netOut.flip();
                    netOutPending = true;

                    if (result.getStatus() == Status.BUFFER_OVERFLOW) {
                        netOut = reallocate(netOut, engine.getSession().getPacketBufferSize());
                        netOutPending = false;
                        continue;
                    }

                    if (!flushNetOut()) {
                        return false;
                    }

                    if (result.getHandshakeStatus() == HandshakeStatus.FINISHED) {
                        finishHandshake();
                        return true;
                    }
                }

                case NEED_UNWRAP, NEED_UNWRAP_AGAIN -> {
                    if (netIn.position() == 0) {
                        int read = channel.read(netIn);
                        if (read == -1) {
                            handleEof();
                            return false;
                        }
                        if (read == 0) {
                            return false;
                        }
                    }

                    netIn.flip();
                    SSLEngineResult result;
                    try {
                        result = engine.unwrap(netIn, appIn);
                    } catch (SSLException e) {
                        // Plaintext on TLS port or corrupted TLS record: abort connection
                        close();
                        session.close();
                        return false;
                    } finally {
                        netIn.compact();
                    }

                    if (result.getStatus() == Status.BUFFER_UNDERFLOW) {
                        return false;
                    }

                    if (result.getStatus() == Status.BUFFER_OVERFLOW) {
                        appIn = reallocate(appIn, engine.getSession().getApplicationBufferSize());
                        continue;
                    }

                    if (result.getStatus() == Status.CLOSED) {
                        close();
                        session.close();
                        return false;
                    }

                    if (result.getHandshakeStatus() == HandshakeStatus.FINISHED) {
                        finishHandshake();
                        return true;
                    }
                }

                case NEED_TASK -> {
                    runDelegatedTasksAsync();
                    return false;
                }

                case FINISHED, NOT_HANDSHAKING -> {
                    finishHandshake();
                    return true;
                }

                default -> throw new IllegalStateException("Unexpected TLS handshake status: " + hs);
            }
        }

        return true;
    }

    private void runDelegatedTasksAsync() {
        TASK_EXECUTOR.submit(() -> {
            Runnable task;
            while ((task = engine.getDelegatedTask()) != null) {
                task.run();
            }
            server.scheduleWrite(session);
        });
    }

    private void finishHandshake() {
        handshaking.set(false);
    }

    /**
     * Reads and decrypts network bytes into application plaintext.
     *
     * @param plaintextConsumer Consumer receiving decrypted plaintext bytes
     * @return Number of plaintext bytes decrypted, or -1 on EOF/close
     */
    public synchronized int readAndDecrypt(Consumer<ByteBuffer> plaintextConsumer) throws IOException {
        if (closed.get()) {
            return -1;
        }

        if (handshaking.get()) {
            if (!processHandshake()) {
                return 0;
            }
        }

        int bytesRead = channel.read(netIn);
        if (bytesRead == -1) {
            handleEof();
            return -1;
        }

        if (netIn.position() == 0) {
            return 0;
        }

        netIn.flip();
        int totalPlaintextProduced = 0;

        try {
            while (netIn.hasRemaining()) {
                appIn.clear();
                SSLEngineResult result;
                try {
                    result = engine.unwrap(netIn, appIn);
                } catch (SSLException e) {
                    close();
                    session.close();
                    return -1;
                }

                if (result.getStatus() == Status.OK) {
                    appIn.flip();
                    if (appIn.hasRemaining()) {
                        totalPlaintextProduced += appIn.remaining();
                        plaintextConsumer.accept(appIn);
                    }
                } else if (result.getStatus() == Status.BUFFER_UNDERFLOW) {
                    break;
                } else if (result.getStatus() == Status.BUFFER_OVERFLOW) {
                    appIn = reallocate(appIn, engine.getSession().getApplicationBufferSize());
                } else if (result.getStatus() == Status.CLOSED) {
                    close();
                    session.close();
                    return -1;
                }

                if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                    runDelegatedTasksAsync();
                    break;
                }
            }
        } finally {
            netIn.compact();
        }

        return totalPlaintextProduced;
    }

    /**
     * Enqueues plaintext application bytes for TLS encryption and non-blocking transmission.
     */
    public void enqueuePlaintext(ByteBuffer plaintext) {
        if (closed.get() || plaintext == null) {
            return;
        }
        outboundPlaintextQueue.offer(plaintext);
        server.scheduleWrite(session);
    }

    /**
     * Handles non-blocking socket writes (encrypts and flushes outgoing TLS frames).
     */
    public synchronized void handleWrite() throws IOException {
        if (closed.get()) {
            return;
        }

        if (handshaking.get()) {
            processHandshake();
            return;
        }

        if (!flushNetOut()) {
            return;
        }

        while (true) {
            if (currentPlaintextWrite == null || !currentPlaintextWrite.hasRemaining()) {
                currentPlaintextWrite = outboundPlaintextQueue.poll();
                if (currentPlaintextWrite == null) {
                    session.disableWriteInterest();
                    break;
                }
            }

            netOut.clear();
            SSLEngineResult result;
            try {
                result = engine.wrap(currentPlaintextWrite, netOut);
            } catch (SSLException e) {
                close();
                session.close();
                return;
            }
            netOut.flip();
            netOutPending = true;

            if (result.getStatus() == Status.BUFFER_OVERFLOW) {
                netOut = reallocate(netOut, engine.getSession().getPacketBufferSize());
                netOutPending = false;
                continue;
            }

            if (!flushNetOut()) {
                return;
            }
        }
    }

    private void handleEof() throws IOException {
        try {
            engine.closeInbound();
        } catch (SSLException ignored) {
        }
        close();
    }

    /**
     * Gracefully initiates TLS close alert and releases buffers.
     */
    public synchronized void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        try {
            engine.closeOutbound();
            if (channel.isOpen()) {
                netOut.clear();
                SSLEngineResult result = engine.wrap(emptyApp, netOut);
                netOut.flip();
                if (result.getStatus() != Status.CLOSED && netOut.hasRemaining()) {
                    channel.write(netOut);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private ByteBuffer reallocate(ByteBuffer buffer, int newCapacity) {
        ByteBuffer newBuffer = ByteBuffer.allocate(Math.max(buffer.capacity() * 2, newCapacity));
        buffer.flip();
        newBuffer.put(buffer);
        return newBuffer;
    }
}
