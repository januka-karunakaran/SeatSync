package com.seatsync.client;

import com.seatsync.common.model.ProtocolConstants;
import com.seatsync.common.protocol.ProtocolJson;
import com.seatsync.common.protocol.ReplyMessage;
import com.seatsync.common.protocol.RequestMessage;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Standard TCP Socket client communicating over persistent streaming socket with the SeatSync server.
 * <p>
 * Key networking features:
 * <ul>
 *   <li>Point-to-point TCP stream establishment using {@link java.net.Socket} and {@link InetSocketAddress}.</li>
 *   <li>Reliable byte-stream framing via UTF-8 newline-delimited JSON payloads.</li>
 *   <li>Dedicated background reader thread to process asynchronous server notifications without blocking the Swing UI.</li>
 *   <li>Thread-safe outbound request transmission and correlation of asynchronous replies.</li>
 * </ul>
 * <p>
 * Owned by Member 01 (TCP Socket Programming & Desktop Client).
 */
public class TcpClient implements AutoCloseable {

    private final String host;
    private final int port;
    private final ClientMessageHandler messageHandler;

    private final Object writeLock = new Object();
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean closing = new AtomicBoolean(false);
    private final AtomicLong requestCounter = new AtomicLong(0);
    private final ConcurrentMap<String, CompletableFuture<ReplyMessage>> pendingRequests = new ConcurrentHashMap<>();

    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private Thread readerThread;

    /**
     * Constructs a TcpClient with configurable host and port.
     *
     * @param host           Remote server hostname or IP address
     * @param port           Remote server TCP port
     * @param messageHandler Message handler for incoming frames
     */
    public TcpClient(String host, int port, ClientMessageHandler messageHandler) {
        this.host = Objects.requireNonNull(host, "host must not be null").trim();
        this.port = port;
        this.messageHandler = (messageHandler != null) ? messageHandler : new ClientMessageHandler();
    }

    /**
     * Constructs a TcpClient with a default message handler.
     *
     * @param host Remote server hostname or IP address
     * @param port Remote server TCP port
     */
    public TcpClient(String host, int port) {
        this(host, port, new ClientMessageHandler());
    }

    /**
     * Constructs a TcpClient pointing to localhost on the standard protocol port.
     */
    public TcpClient() {
        this("127.0.0.1", ProtocolConstants.DEFAULT_PORT);
    }

    /**
     * Connects to the remote server within the given timeout period.
     *
     * @param timeoutMs Connection timeout in milliseconds
     * @throws IOException if the connection attempt fails or is refused
     */
    public synchronized void connect(int timeoutMs) throws IOException {
        if (connected.get()) {
            return;
        }

        closing.set(false);
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setTcpNoDelay(true);

        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), ProtocolConstants.CHARSET));
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), ProtocolConstants.CHARSET));

        connected.set(true);
        startReaderThread();
        messageHandler.handleConnected();
    }

    /**
     * Starts the dedicated background thread that reads lines from the TCP socket stream.
     */
    private void startReaderThread() {
        readerThread = new Thread(() -> {
            try {
                String line;
                while (connected.get() && !closing.get() && (line = reader.readLine()) != null) {
                    messageHandler.handleIncomingLine(line, pendingRequests);
                }
            } catch (IOException e) {
                if (!closing.get()) {
                    messageHandler.handleConnectionError(e);
                }
            } finally {
                disconnectInternal("Socket connection closed by remote peer or stream ended");
            }
        }, "seatsync-tcp-reader-" + host + ":" + port);

        readerThread.setDaemon(true);
        readerThread.start();
    }

    /**
     * Sends a protocol request message and returns a CompletableFuture tracking the server reply.
     *
     * @param request Request message to transmit
     * @return Future completed when matching ReplyMessage is returned
     * @throws IOException if transmitting data fails or socket is not open
     */
    public CompletableFuture<ReplyMessage> sendRequest(RequestMessage request) throws IOException {
        if (!connected.get() || closing.get()) {
            throw new IOException("Cannot send request: TCP client is not connected to " + host + ":" + port);
        }

        CompletableFuture<ReplyMessage> future = new CompletableFuture<>();
        pendingRequests.put(request.requestId(), future);

        String frame = ProtocolJson.serializeToWire(request);
        synchronized (writeLock) {
            writer.write(frame);
            writer.flush();
        }

        return future;
    }

    /**
     * Sends a raw newline-delimited JSON string over the wire.
     *
     * @param json JSON frame string
     * @throws IOException if write fails
     */
    public void sendRawLine(String json) throws IOException {
        if (!connected.get() || closing.get()) {
            throw new IOException("Cannot send line: TCP client is not connected to " + host + ":" + port);
        }

        String wire = json.endsWith("\n") ? json : json + ProtocolConstants.LINE_DELIMITER;
        synchronized (writeLock) {
            writer.write(wire);
            writer.flush();
        }
    }

    /**
     * Generates a unique request identifier for correlation.
     *
     * @return Unique requestId (e.g. "REQ-1")
     */
    public String nextRequestId() {
        return "REQ-" + requestCounter.incrementAndGet();
    }

    /**
     * Gracefully disconnects from the server and closes all socket streams.
     */
    public synchronized void disconnect() {
        disconnectInternal("Client requested normal disconnect");
    }

    private void disconnectInternal(String reason) {
        if (!connected.compareAndSet(true, false)) {
            return;
        }

        closing.set(true);

        try {
            if (writer != null) {
                writer.close();
            }
        } catch (IOException ignored) {
        }

        try {
            if (reader != null) {
                reader.close();
            }
        } catch (IOException ignored) {
        }

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }

        if (readerThread != null && Thread.currentThread() != readerThread) {
            try {
                readerThread.join(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        messageHandler.handleDisconnected(reason, pendingRequests);
    }

    @Override
    public void close() {
        disconnect();
    }

    public boolean isConnected() {
        return connected.get() && socket != null && socket.isConnected() && !socket.isClosed();
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public ClientMessageHandler getMessageHandler() {
        return messageHandler;
    }
}
