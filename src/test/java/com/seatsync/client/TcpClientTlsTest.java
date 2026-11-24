package com.seatsync.client;

import com.seatsync.common.protocol.reply.LoginOkReply;
import com.seatsync.common.protocol.request.LoginRequest;
import com.seatsync.network.NioBookingServer;
import com.seatsync.network.ServerConfig;
import com.seatsync.network.tls.TlsTestHelper;
import com.seatsync.server.MockRequestDispatcher;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TcpClientTlsTest {
    private NioBookingServer server;
    private TlsTestHelper.SslContextBundle contexts;

    @BeforeEach
    void startTlsAndPlainServer() throws IOException {
        contexts = TlsTestHelper.getOrCreateBundle();
        server = new NioBookingServer(ServerConfig.withDualTransport(
            ServerConfig.DEFAULT_HOST, 0, 0, contexts.serverContext()), new MockRequestDispatcher());
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void trustedTlsClientConnectsAndExchangesProtocolRequest() throws Exception {
        try (TcpClient client = new TcpClient("127.0.0.1", server.getTlsPort(), null, contexts.trustedClientContext())) {
            client.connect(3000);
            assertTrue(client.isTlsEnabled());
            assertInstanceOf(LoginOkReply.class, client.sendRequest(
                new LoginRequest(client.nextRequestId(), "alice", "local-test")).get(3, TimeUnit.SECONDS));
        }
    }

    @Test
    void tlsClientRemainsConnectedAfterTheHandshakeTimeoutWindow() throws Exception {
        try (TcpClient client = new TcpClient("127.0.0.1", server.getTlsPort(), null, contexts.trustedClientContext())) {
            client.connect(3000);
            Thread.sleep(3500);

            assertTrue(client.isConnected());
            assertInstanceOf(LoginOkReply.class, client.sendRequest(
                new LoginRequest(client.nextRequestId(), "alice", "local-test")).get(3, TimeUnit.SECONDS));
        }
    }

    @Test
    void untrustedCertificateIsRejectedWithoutPlaintextRetry() {
        TcpClient client = new TcpClient("127.0.0.1", server.getTlsPort(), null, contexts.untrustedClientContext());
        assertThrows(SSLException.class, () -> client.connect(3000));
        assertFalse(client.isConnected());
    }

    @Test
    void tlsClientDoesNotDowngradeToPlaintextEndpoint() {
        TcpClient client = new TcpClient("127.0.0.1", server.getPort(), null, contexts.trustedClientContext());
        assertThrows(IOException.class, () -> client.connect(1500));
        assertFalse(client.isConnected());
        assertTrue(client.isTlsEnabled());
    }
}
