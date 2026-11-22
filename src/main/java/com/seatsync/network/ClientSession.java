package com.seatsync.network;

import com.seatsync.common.contracts.RequestContext;
import com.seatsync.common.contracts.RequestDispatcher;
import com.seatsync.common.model.ErrorCode;
import com.seatsync.common.model.ProtocolConstants;
import com.seatsync.common.protocol.ProtocolJson;
import com.seatsync.common.protocol.reply.ErrorReply;
import com.seatsync.network.metrics.NetworkMetricsTracker;
import com.seatsync.network.tls.TlsHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Encapsulates an active client connection on the Java NIO server.
 * Implements {@link RequestContext} to provide thread-safe outbound messaging and session state.
 * <p>
 * Manages ByteBuffer accumulation for partial lines, multiple lines per read chunk,
 * frame size limit enforcement (16 KiB), and partial write handling via OP_WRITE.
 */
public class ClientSession implements RequestContext {

    private final String sessionId;
    private final SocketChannel channel;
    private final NioBookingServer server;
    private final int maxFrameBytes;
    private final ByteBuffer readBuffer;
    private final ByteArrayOutputStream inputAccumulator = new ByteArrayOutputStream(1024);
    private final ConcurrentLinkedQueue<ByteBuffer> outboundQueue = new ConcurrentLinkedQueue<>();
    private final AtomicReference<String> authenticatedUserId = new AtomicReference<>(null);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final NetworkMetricsTracker metricsTracker;
    private final long connectedAtMs = System.currentTimeMillis();

    private volatile SelectionKey selectionKey;
    private volatile TlsHandler tlsHandler;
    private ByteBuffer currentWriteBuffer = null;

    public ClientSession(
        SocketChannel channel,
        NioBookingServer server,
        int maxFrameBytes,
        int readBufferSize,
        NetworkMetricsTracker metricsTracker
    ) {
        this.sessionId = UUID.randomUUID().toString();
        this.channel = channel;
        this.server = server;
        this.maxFrameBytes = maxFrameBytes;
        this.readBuffer = ByteBuffer.allocate(readBufferSize);
        this.metricsTracker = metricsTracker;
    }

    public ClientSession(SocketChannel channel, NioBookingServer server, int maxFrameBytes, int readBufferSize) {
        this(channel, server, maxFrameBytes, readBufferSize, null);
    }

    public void setSelectionKey(SelectionKey key) {
        this.selectionKey = key;
    }

    public SelectionKey getSelectionKey() {
        return selectionKey;
    }

    public void setTlsHandler(TlsHandler tlsHandler) {
        this.tlsHandler = tlsHandler;
    }

    public TlsHandler getTlsHandler() {
        return tlsHandler;
    }

    public boolean isTls() {
        return tlsHandler != null;
    }

    public SocketChannel getChannel() {
        return channel;
    }

    @Override
    public String getSessionId() {
        return sessionId;
    }

    @Override
    public Optional<String> getAuthenticatedUserId() {
        return Optional.ofNullable(authenticatedUserId.get());
    }

    @Override
    public synchronized void setAuthenticatedUserId(String userId) {
        authenticatedUserId.set(userId);
    }

    @Override
    public synchronized void clearAuthentication() {
        authenticatedUserId.set(null);
    }

    /** Atomically check the current session user and queue a private event. */
    public synchronized void sendToUser(String userId, String line) {
        if (userId.equals(authenticatedUserId.get())) {
            send(line);
        }
    }

    @Override
    public boolean isOpen() {
        return !closed.get() && channel.isOpen();
    }

    /**
     * Thread-safe outbound send API. Can be called from any thread (worker threads, broadcaster).
     * Enqueues the frame and notifies the selector thread via selector.wakeup().
     *
     * @param jsonLine Line of JSON (LF delimiter will be appended if absent)
     */
    @Override
    public void send(String jsonLine) {
        if (!isOpen() || jsonLine == null) {
            return;
        }

        String wireLine = jsonLine.endsWith(ProtocolConstants.LINE_DELIMITER)
            ? jsonLine
            : jsonLine + ProtocolConstants.LINE_DELIMITER;

        byte[] bytes = wireLine.getBytes(ProtocolConstants.CHARSET);
        ByteBuffer buffer = ByteBuffer.wrap(bytes);

        if (tlsHandler != null) {
            tlsHandler.enqueuePlaintext(buffer);
        } else {
            outboundQueue.offer(buffer);
            server.scheduleWrite(this);
        }
    }

    /**
     * Handles incoming bytes on the selector thread when the channel is readable (OP_READ).
     *
     * @param dispatcher RequestDispatcher responsible for handling complete JSON lines
     * @throws IOException if a low-level network I/O error occurs
     */
    public void handleRead(RequestDispatcher dispatcher) throws IOException {
        if (!isOpen()) {
            return;
        }

        if (tlsHandler != null) {
            int decrypted = tlsHandler.readAndDecrypt(plaintextBuf -> {
                if (metricsTracker != null) {
                    metricsTracker.recordBytesIn(plaintextBuf.remaining());
                }
                processIncomingBytes(plaintextBuf, dispatcher);
            });
            if (decrypted == -1) {
                close();
            }
            return;
        }

        readBuffer.clear();
        int bytesRead = channel.read(readBuffer);

        if (bytesRead > 0 && metricsTracker != null) {
            metricsTracker.recordBytesIn(bytesRead);
        }

        if (bytesRead == -1) {
            // EOF: client gracefully disconnected or closed socket
            close();
            return;
        }

        if (bytesRead == 0) {
            return;
        }

        readBuffer.flip();
        processIncomingBytes(readBuffer, dispatcher);
    }

    private void processIncomingBytes(ByteBuffer buffer, RequestDispatcher dispatcher) {
        CharsetDecoder decoder = ProtocolConstants.CHARSET.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);

        while (buffer.hasRemaining()) {
            byte b = buffer.get();
            if (b == '\n') {
                // Line terminator encountered
                byte[] lineBytes = inputAccumulator.toByteArray();
                inputAccumulator.reset();

                if (lineBytes.length > maxFrameBytes) {
                    sendPayloadTooLargeAndClose();
                    return;
                }

                // Decode lineBytes to UTF-8 string
                String line;
                try {
                    ByteBuffer lineBuffer = ByteBuffer.wrap(lineBytes);
                    line = decoder.decode(lineBuffer).toString();
                } catch (CharacterCodingException e) {
                    // Invalid UTF-8 received
                    sendErrorReply(null, ErrorCode.INVALID_REQUEST_FORMAT, "Invalid UTF-8 encoding in request");
                    continue;
                }

                // Strip optional leading UTF-8 BOM (\uFEFF)
                if (line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }

                // Strip optional trailing \r (CRLF support)
                if (line.endsWith("\r")) {
                    line = line.substring(0, line.length() - 1);
                }

                // Ignore empty lines
                if (line.trim().isEmpty()) {
                    continue;
                }

                // Dispatch to business layer
                if (metricsTracker != null) {
                    metricsTracker.recordJsonFrame();
                }
                if (dispatcher != null) {
                    dispatcher.dispatch(this, line);
                }
            } else {
                inputAccumulator.write(b);
                if (inputAccumulator.size() > maxFrameBytes) {
                    sendPayloadTooLargeAndClose();
                    return;
                }
            }
        }
    }

    /**
     * Handles non-blocking socket writes on the selector thread when the channel is writable (OP_WRITE).
     * NEVER assumes a single write transmits the entire buffer.
     *
     * @throws IOException if a network write error occurs
     */
    public void handleWrite() throws IOException {
        if (!isOpen()) {
            return;
        }

        if (tlsHandler != null) {
            tlsHandler.handleWrite();
            return;
        }

        while (true) {
            if (currentWriteBuffer == null || !currentWriteBuffer.hasRemaining()) {
                currentWriteBuffer = outboundQueue.poll();
                if (currentWriteBuffer == null) {
                    // All pending outbound frames have been written
                    disableWriteInterest();
                    break;
                }
            }

            int written = channel.write(currentWriteBuffer);
            if (written > 0 && metricsTracker != null) {
                metricsTracker.recordBytesOut(written);
            }
            if (written == 0) {
                // OS socket buffer full, cannot write more right now
                enableWriteInterest();
                break;
            }

            if (currentWriteBuffer.hasRemaining()) {
                // Partial write occurred: keep OP_WRITE enabled and resume later
                enableWriteInterest();
                break;
            }
        }

        if (outboundQueue.isEmpty() && (currentWriteBuffer == null || !currentWriteBuffer.hasRemaining())) {
            currentWriteBuffer = null;
            disableWriteInterest();
        }
    }

    public void enableWriteInterest() {
        SelectionKey key = this.selectionKey;
        if (key != null && key.isValid()) {
            key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
        }
    }

    public void disableWriteInterest() {
        SelectionKey key = this.selectionKey;
        if (key != null && key.isValid()) {
            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
        }
    }

    private void sendPayloadTooLargeAndClose() {
        if (metricsTracker != null) {
            metricsTracker.recordRejectedFrame();
        }
        try {
            ErrorReply reply = new ErrorReply(
                "N/A",
                ErrorCode.PAYLOAD_TOO_LARGE,
                "Request frame exceeded limit of " + maxFrameBytes + " bytes"
            );
            byte[] bytes = ProtocolJson.serializeToWire(reply).getBytes(ProtocolConstants.CHARSET);
            channel.write(ByteBuffer.wrap(bytes));
        } catch (Exception ignored) {
        } finally {
            close();
        }
    }

    private void sendErrorReply(String requestId, ErrorCode errorCode, String message) {
        ErrorReply errorReply = new ErrorReply(
            requestId == null ? "N/A" : requestId,
            errorCode,
            message
        );
        send(ProtocolJson.serialize(errorReply));
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            if (tlsHandler != null) {
                try {
                    tlsHandler.close();
                } catch (Exception ignored) {
                }
            }
            if (metricsTracker != null) {
                metricsTracker.recordClosedConnection(System.currentTimeMillis() - connectedAtMs);
            }
            try {
                SelectionKey key = this.selectionKey;
                if (key != null) {
                    key.cancel();
                }
            } catch (Exception ignored) {
            }

            try {
                if (channel.isOpen()) {
                    channel.close();
                }
            } catch (Exception ignored) {
            }

            outboundQueue.clear();
            inputAccumulator.reset();
            server.getSessionRegistry().unregister(sessionId);
        }
    }
}
