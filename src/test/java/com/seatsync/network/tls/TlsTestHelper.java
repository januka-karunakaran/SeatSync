package com.seatsync.network.tls;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.security.KeyStore;

/**
 * Test helper for generating dynamic in-memory TLS 1.3 {@link SSLContext} instances
 * for integration tests without committing private keys to the repository.
 */
public final class TlsTestHelper {

    private static final char[] PASSWORD = "test-password-123".toCharArray();
    private static volatile SslContextBundle bundle;

    private TlsTestHelper() {
        // utility class
    }

    public record SslContextBundle(
        SSLContext serverContext,
        SSLContext trustedClientContext,
        SSLContext untrustedClientContext
    ) {
    }

    /**
     * Lazily creates and caches a dynamic TLS 1.3 self-signed test certificate bundle.
     */
    public static synchronized SslContextBundle getOrCreateBundle() {
        if (bundle != null) {
            return bundle;
        }

        try {
            File tempKeystore = File.createTempFile("seatsync-test-keystore", ".p12");
            tempKeystore.delete();
            tempKeystore.deleteOnExit();

            // Run JDK keytool to generate a self-signed RSA keypair with SAN=127.0.0.1,localhost
            ProcessBuilder pb = new ProcessBuilder(
                "keytool",
                "-genkeypair",
                "-alias", "seatsync-test",
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "1",
                "-keystore", tempKeystore.getAbsolutePath(),
                "-storetype", "PKCS12",
                "-storepass", new String(PASSWORD),
                "-keypass", new String(PASSWORD),
                "-dname", "CN=localhost, OU=Testing, O=SeatSync, C=LK",
                "-ext", "SAN=dns:localhost,ip:127.0.0.1"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("keytool failed with exit code: " + exitCode + ", output: " + output);
            }

            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (FileInputStream fis = new FileInputStream(tempKeystore)) {
                keyStore.load(fis, PASSWORD);
            }

            // Server SSLContext: uses keyStore as KeyManager and TrustManager
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, PASSWORD);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);

            SSLContext serverContext = SSLContext.getInstance("TLSv1.3");
            serverContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

            // Trusted Client SSLContext: trusts the server's certificate
            SSLContext clientContext = SSLContext.getInstance("TLSv1.3");
            clientContext.init(null, tmf.getTrustManagers(), null);

            // Untrusted Client SSLContext: empty truststore (rejects server certificate)
            KeyStore emptyStore = KeyStore.getInstance("PKCS12");
            emptyStore.load(null, PASSWORD);
            TrustManagerFactory untrustedTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            untrustedTmf.init(emptyStore);

            SSLContext untrustedClientContext = SSLContext.getInstance("TLSv1.3");
            untrustedClientContext.init(null, untrustedTmf.getTrustManagers(), null);

            Files.deleteIfExists(tempKeystore.toPath());

            bundle = new SslContextBundle(serverContext, clientContext, untrustedClientContext);
            return bundle;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate test TLS context: " + e.getMessage(), e);
        }
    }
}
