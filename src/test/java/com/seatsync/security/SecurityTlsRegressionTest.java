package com.seatsync.security;

import com.seatsync.common.model.ProtocolConstants;
import com.seatsync.common.model.TlsConfigMode;
import com.seatsync.common.protocol.ProtocolJson;
import com.seatsync.concurrency.WorkerRequestDispatcher;
import com.seatsync.network.NioBookingServer;
import com.seatsync.network.ServerConfig;
import com.seatsync.security.tls.TlsContextFactory;
import com.seatsync.security.tls.TlsServerConfiguration;
import com.seatsync.security.tls.TrustedTlsSocket;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Real M2 NIO TLS transport using M4 key/trust loading and certificate identity policy. */
class SecurityTlsRegressionTest {
    @TempDir static Path temporary;
    private static Material valid;
    private static Material wrongHost;
    private static Material expired;

    private record Material(Path keys, Path trust, char[] password) {
        SSLContext server() throws Exception { return TlsContextFactory.server(keys, password.clone()); }
        SSLContext client() throws Exception { return TlsContextFactory.client(trust, password.clone()); }
    }

    @BeforeAll static void certificates() throws Exception {
        valid = generate("valid", "localhost", false);
        wrongHost = generate("wrong-host", "other.example", false);
        expired = generate("expired", "localhost", true);
        AdminSecurityTest.accounts();
    }

    private static Material generate(String name, String host, boolean expiredCertificate) throws Exception {
        Path keys = temporary.resolve(name + ".p12");
        Path trust = temporary.resolve(name + "-trust.p12");
        byte[] entropy = new byte[24];
        new SecureRandom().nextBytes(entropy);
        char[] password = Base64.getEncoder().encodeToString(entropy).toCharArray();
        Path keytool = Path.of(System.getProperty("java.home"), "bin",
            System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool");
        var command = new java.util.ArrayList<>(List.of(keytool.toString(), "-genkeypair", "-alias", "server",
            "-keyalg", "RSA", "-keysize", "2048", "-validity", "1", "-storetype", "PKCS12", "-keystore", keys.toString(),
            "-storepass:env", "SEATSYNC_TEST_STORE_PASSWORD", "-keypass:env", "SEATSYNC_TEST_STORE_PASSWORD",
            "-dname", "CN=" + host, "-ext", "SAN=dns:" + host + (host.equals("localhost") ? ",ip:127.0.0.1" : "")));
        if (expiredCertificate) { command.addAll(List.of("-startdate", "-2d")); }
        var processBuilder = new ProcessBuilder(command).redirectErrorStream(true);
        processBuilder.environment().put("SEATSYNC_TEST_STORE_PASSWORD", new String(password));
        // Redirect to a temporary file so waitFor is bounded even if keytool is unexpectedly verbose.
        processBuilder.redirectOutput(temporary.resolve(name + "-keytool.log").toFile());
        var process = processBuilder.start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Test certificate generation timed out");
        }
        assertEquals(0, process.exitValue(), "Test certificate generation failed");
        KeyStore source = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(keys)) { source.load(input, password); }
        KeyStore publicTrust = KeyStore.getInstance("PKCS12");
        publicTrust.load(null, password);
        publicTrust.setCertificateEntry("server", source.getCertificate("server"));
        try (var output = Files.newOutputStream(trust)) { publicTrust.store(output, password); }
        return new Material(keys, trust, password);
    }

    private static final class SecureServer implements AutoCloseable {
        final AdminSecurityTest.Harness harness = new AdminSecurityTest.Harness();
        final WorkerRequestDispatcher workers = new WorkerRequestDispatcher(harness.dispatcher);
        final NioBookingServer server;
        SecureServer(Material material) throws Exception {
            server = new NioBookingServer(new ServerConfig("127.0.0.1", 0, ProtocolConstants.MAX_FRAME_BYTES,
                4096, TlsConfigMode.REQUIRED, 0, material.server(), false, 0, null), workers);
            server.start();
        }
        int port() { return server.getTlsPort(); }
        @Override public void close() { server.stop(); workers.close(); }
    }

    @Test void factoryConsumesPasswordsAndRejectsMissingEmptyWrongAndExpiredMaterial() throws Exception {
        char[] password = valid.password().clone();
        assertNotNull(TlsContextFactory.server(valid.keys(), password));
        assertTrue(new String(password).chars().allMatch(c -> c == 0));
        char[] wrong = "test-only-wrong-password".toCharArray();
        assertThrows(java.io.IOException.class, () -> TlsContextFactory.client(valid.trust(), wrong));
        assertTrue(new String(wrong).chars().allMatch(c -> c == 0));
        assertThrows(java.security.GeneralSecurityException.class, () -> TlsContextFactory.client(temporary.resolve("missing.p12"), valid.password().clone()));
        assertThrows(java.security.GeneralSecurityException.class, () -> TlsContextFactory.client(valid.keys(), valid.password().clone()));
        assertThrows(java.security.cert.CertificateExpiredException.class, expired::server);
        assertThrows(java.security.cert.CertificateExpiredException.class, expired::client);
        Path empty = temporary.resolve("empty.p12");
        var store = KeyStore.getInstance("PKCS12");
        store.load(null, valid.password());
        try (var output = Files.newOutputStream(empty)) { store.store(output, valid.password()); }
        assertThrows(java.security.GeneralSecurityException.class, () -> TlsContextFactory.client(empty, valid.password().clone()));
    }

    @Test void requiredConfigFailsClosedWithoutSecretsAndPlaintextIsLoopbackOnly() {
        assertThrows(IllegalArgumentException.class, () -> TlsServerConfiguration.fromEnvironment("127.0.0.1", 8080,
            Map.of("SEATSYNC_TLS_MODE", "REQUIRED")));
        assertThrows(IllegalArgumentException.class, () -> TlsServerConfiguration.fromEnvironment("0.0.0.0", 8080, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> TlsServerConfiguration.fromEnvironment("127.0.0.1", 8080,
            Map.of("SEATSYNC_TLS_MODE", "OPTIONAL")));
        assertThrows(IllegalArgumentException.class, () -> TlsContextFactory.clientFromEnvironment(Map.of()));
    }

    @Test void trustedTls13ConnectionEnforcesAdminPolicyOverRealNio() throws Exception {
        try (var server = new SecureServer(valid);
             var socket = TrustedTlsSocket.connect(valid.client(), "localhost", server.port(), 5000)) {
            assertEquals("TLSv1.3", socket.getSession().getProtocol());
            var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            var writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
            writer.write("{\"type\":\"GET_NETWORK_METRICS\",\"requestId\":\"anonymous\"}\n"); writer.flush();
            assertEquals("AUTH_REQUIRED", ProtocolJson.parseTree(reader.readLine()).path("errorCode").asText());
            writer.write(ProtocolJson.serialize(Map.of("type", "LOGIN", "requestId", "login", "username", "alice",
                "password", "test-only-customer-password")) + "\n"); writer.flush();
            assertEquals("LOGIN_OK", ProtocolJson.parseTree(reader.readLine()).path("type").asText());
            writer.write("{\"type\":\"GET_NETWORK_METRICS\",\"requestId\":\"customer\"}\n"); writer.flush();
            assertEquals("FORBIDDEN_ROLE", ProtocolJson.parseTree(reader.readLine()).path("errorCode").asText());
            writer.write("{\"type\":\"ADMIN_GET_AUDIT_LOGS\",\"requestId\":\"forged\",\"limit\":1,\"role\":\"ADMIN\"}\n"); writer.flush();
            assertEquals("INVALID_REQUEST_FORMAT", ProtocolJson.parseTree(reader.readLine()).path("errorCode").asText());
            writer.write("{\"type\":\"LOGOUT\",\"requestId\":\"logout\"}\n"); writer.flush(); reader.readLine();
            writer.write(ProtocolJson.serialize(Map.of("type", "LOGIN", "requestId", "admin-login", "username", "admin",
                "password", "test-only-separate-admin-password")) + "\n"); writer.flush();
            assertEquals("LOGIN_OK", ProtocolJson.parseTree(reader.readLine()).path("type").asText());
            writer.write("{\"type\":\"GET_NETWORK_METRICS\",\"requestId\":\"admin\"}\n"); writer.flush();
            assertEquals("NETWORK_METRICS_REPLY", ProtocolJson.parseTree(reader.readLine()).path("type").asText());
            var movie = new com.seatsync.common.model.MovieDetails("TLS-MOVIE", "TLS movie", "Description", "Drama", 90);
            writer.write(ProtocolJson.serialize(new com.seatsync.common.protocol.request.AdminCreateMovieRequest("movie", movie)) + "\n");
            writer.flush();
            assertEquals("ADMIN_MOVIE_CREATED", ProtocolJson.parseTree(reader.readLine()).path("type").asText());
            writer.write(ProtocolJson.serialize(new com.seatsync.common.protocol.request.AdminCreateShowRequest("show", movie.movieId(),
                AdminSecurityTest.show("TLS-SHOW"))) + "\n"); writer.flush();
            assertEquals("ADMIN_SHOW_ADDED", ProtocolJson.parseTree(reader.readLine()).path("type").asText());
            writer.write("{\"type\":\"ADMIN_DISABLE_SHOW\",\"requestId\":\"disable\",\"showId\":\"TLS-SHOW\"}\n"); writer.flush();
            assertEquals("ADMIN_SHOW_DISABLED", ProtocolJson.parseTree(reader.readLine()).path("type").asText());
            writer.write("{\"type\":\"BOOK_SEAT\",\"requestId\":\"disabled\",\"showId\":\"TLS-SHOW\",\"seatId\":\"A1\"}\n"); writer.flush();
            assertEquals("SHOW_DISABLED", ProtocolJson.parseTree(reader.readLine()).path("errorCode").asText());
        }
    }

    @Test void untrustedAndWrongHostnameCertificatesAreRejected() throws Exception {
        try (var server = new SecureServer(valid)) {
            assertThrows(SSLException.class, () -> TrustedTlsSocket.connect(wrongHost.client(), "localhost", server.port(), 5000));
        }
        try (var server = new SecureServer(wrongHost)) {
            assertThrows(SSLException.class, () -> TrustedTlsSocket.connect(wrongHost.client(), "localhost", server.port(), 5000));
        }
    }

    @Test void tls12AndPlaintextCannotUseRequiredTlsPort() throws Exception {
        try (var server = new SecureServer(valid)) {
            try (SSLSocket socket = (SSLSocket) valid.client().getSocketFactory().createSocket("localhost", server.port())) {
                socket.setSoTimeout(5000);
                socket.setEnabledProtocols(new String[] {"TLSv1.2"});
                assertThrows(SSLException.class, socket::startHandshake);
            }
            try (var socket = new Socket("127.0.0.1", server.port())) {
                socket.setSoTimeout(5000);
                socket.getOutputStream().write("{\"type\":\"LOGIN\",\"requestId\":\"plain\"}\n".getBytes(StandardCharsets.UTF_8));
                assertEquals(-1, socket.getInputStream().read(), "Plaintext must be closed, never processed as JSON");
            }
        }
    }

    @Test void malformedAndOversizedFramesAreRejectedAfterTlsDecryption() throws Exception {
        try (var server = new SecureServer(valid)) {
            try (var socket = TrustedTlsSocket.connect(valid.client(), "127.0.0.1", server.port(), 5000)) {
                socket.getOutputStream().write("{broken\n".getBytes(StandardCharsets.UTF_8));
                socket.getOutputStream().flush();
                var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                assertEquals("INVALID_REQUEST_FORMAT", ProtocolJson.parseTree(reader.readLine()).path("errorCode").asText());
            }
            try (var socket = TrustedTlsSocket.connect(valid.client(), "127.0.0.1", server.port(), 5000)) {
                socket.getOutputStream().write("x".repeat(ProtocolConstants.MAX_FRAME_BYTES + 1).getBytes(StandardCharsets.UTF_8));
                socket.getOutputStream().flush();
                // Transport may send a TLS error response or simply close, but cannot process this as a request.
                try {
                    int firstByte = socket.getInputStream().read();
                    assertEquals(-1, firstByte, "Oversized TLS frame must close the connection");
                } catch (SSLException closed) { /* Closed by the NIO frame limit. */ }
            }
        }
    }

    @Test void configuredClientEnginesEnableHostnameValidationAndOnlyTls13() throws Exception {
        var engine = TlsContextFactory.clientEngine(valid.client(), "localhost", 8443);
        assertArrayEquals(new String[] {"TLSv1.3"}, engine.getEnabledProtocols());
        assertEquals("HTTPS", engine.getSSLParameters().getEndpointIdentificationAlgorithm());
        assertArrayEquals(new String[] {"TLSv1.3"}, TlsContextFactory.serverEngine(valid.server()).getEnabledProtocols());
    }
}
