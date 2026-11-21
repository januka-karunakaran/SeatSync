package com.seatsync.client;

import com.seatsync.common.model.ProtocolConstants;
import com.seatsync.common.protocol.ReplyMessage;
import com.seatsync.common.protocol.reply.LoginOkReply;
import com.seatsync.common.protocol.request.LoginRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit and integration tests for {@link TcpClient}.
 * Validates TCP connection establishment, message framing, asynchronous
 * reader thread execution, request correlation, and disconnect lifecycle.
 * <p>
 * Owned by Member 01 (TCP Socket Programming & Desktop Client).
 */
class TcpClientTest {

    private ServerSocket serverSocket;
    private TcpClient client;

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.disconnect();
        }
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
    }

    @Test
    @DisplayName("TCP connection establishes, transmits framed JSON request and correlates reply")
    void testConnectSendAndCorrelateReply() throws Exception {
        serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();

        CountDownLatch serverDoneLatch = new CountDownLatch(1);

        // Mock Server thread
        new Thread(() -> {
            try (Socket accepted = serverSocket.accept();
                 BufferedReader in = new BufferedReader(new InputStreamReader(accepted.getInputStream(), ProtocolConstants.CHARSET));
                 BufferedWriter out = new BufferedWriter(new OutputStreamWriter(accepted.getOutputStream(), ProtocolConstants.CHARSET))) {

                String line = in.readLine();
                assertNotNull(line, "Server must receive request line");
                assertTrue(line.contains("LOGIN"));
                assertTrue(line.contains("REQ-1"));

                // Reply with LoginOkReply
                String replyJson = "{\"type\":\"LOGIN_OK\",\"requestId\":\"REQ-1\",\"userId\":\"USR-101\",\"username\":\"alice\"}\n";
                out.write(replyJson);
                out.flush();
                serverDoneLatch.countDown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        client = new TcpClient("127.0.0.1", port);
        client.connect(2000);
        assertTrue(client.isConnected());

        LoginRequest request = new LoginRequest("REQ-1", "alice", "secret");
        CompletableFuture<ReplyMessage> future = client.sendRequest(request);

        ReplyMessage reply = future.get(3, TimeUnit.SECONDS);
        assertInstanceOf(LoginOkReply.class, reply);

        LoginOkReply ok = (LoginOkReply) reply;
        assertEquals("REQ-1", ok.requestId());
        assertEquals("USR-101", ok.userId());
        assertEquals("alice", ok.username());

        assertTrue(serverDoneLatch.await(2, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Connection refusal throws IOException gracefully")
    void testConnectionRefused() {
        // Pick an unused local port
        client = new TcpClient("127.0.0.1", 59999);
        assertThrows(IOException.class, () -> client.connect(500));
        assertFalse(client.isConnected());
    }

    @Test
    @DisplayName("Listener receives disconnect notification when remote closes socket")
    void testRemoteDisconnectNotification() throws Exception {
        serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();
        CountDownLatch disconnectLatch = new CountDownLatch(1);

        ClientMessageHandler handler = new ClientMessageHandler();
        handler.addListener(new ClientMessageHandler.Listener() {
            @Override
            public void onDisconnected(String reason) {
                disconnectLatch.countDown();
            }
        });

        client = new TcpClient("127.0.0.1", port, handler);

        // Server accepts then immediately closes
        new Thread(() -> {
            try {
                Socket accepted = serverSocket.accept();
                Thread.sleep(100);
                accepted.close();
            } catch (Exception ignored) {
            }
        }).start();

        client.connect(2000);
        assertTrue(client.isConnected());

        assertTrue(disconnectLatch.await(3, TimeUnit.SECONDS), "Client must receive onDisconnected event");
        assertFalse(client.isConnected());
    }

    @Test
    @DisplayName("Malformed incoming line notifies listener without terminating connection")
    void testMalformedMessageHandling() throws Exception {
        serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();
        CountDownLatch malformedLatch = new CountDownLatch(1);
        AtomicBoolean receivedMalformed = new AtomicBoolean(false);

        ClientMessageHandler handler = new ClientMessageHandler();
        handler.addListener(new ClientMessageHandler.Listener() {
            @Override
            public void onMalformedMessage(String rawJson, String errorMessage) {
                receivedMalformed.set(true);
                malformedLatch.countDown();
            }
        });

        client = new TcpClient("127.0.0.1", port, handler);

        new Thread(() -> {
            try (Socket s = serverSocket.accept();
                 BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), ProtocolConstants.CHARSET))) {
                out.write("NOT_A_VALID_JSON_OBJECT\n");
                out.flush();
                Thread.sleep(500);
            } catch (Exception ignored) {
            }
        }).start();

        client.connect(2000);
        assertTrue(malformedLatch.await(3, TimeUnit.SECONDS));
        assertTrue(receivedMalformed.get());
    }
}
