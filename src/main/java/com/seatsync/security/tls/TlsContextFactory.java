package com.seatsync.security.tls;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

/** Explicit PKCS12 trust/key loading shared by M2 NIO and M5 client. No trust-all/default-trust fallback. */
public final class TlsContextFactory {
    public static final String PROTOCOL = "TLSv1.3";
    private TlsContextFactory() {}

    /** Consumes and clears password, including on failure. */
    public static SSLContext server(Path keyStore, char[] password) throws IOException, GeneralSecurityException {
        try {
            KeyStore keys = load(keyStore, password);
            boolean hasKey = false;
            for (var aliases = keys.aliases(); aliases.hasMoreElements();) {
                String alias = aliases.nextElement();
                if (keys.isKeyEntry(alias)) {
                    var chain = keys.getCertificateChain(alias);
                    if (chain == null || chain.length == 0) { throw new GeneralSecurityException("Missing certificate chain"); }
                    for (var certificate : chain) { ((X509Certificate) certificate).checkValidity(); }
                    hasKey = true;
                }
            }
            if (!hasKey) { throw new GeneralSecurityException("Server store has no private key"); }
            KeyManagerFactory manager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            manager.init(keys, password);
            SSLContext context = SSLContext.getInstance(PROTOCOL);
            context.init(manager.getKeyManagers(), null, new SecureRandom());
            return context;
        } finally { clear(password); }
    }

    /** Explicit truststore required. Consumes and clears password. Never imports keys from trust material. */
    public static SSLContext client(Path trustStore, char[] password) throws IOException, GeneralSecurityException {
        try {
            KeyStore trust = load(trustStore, password);
            if (trust.size() == 0) { throw new GeneralSecurityException("Truststore is empty"); }
            for (var aliases = trust.aliases(); aliases.hasMoreElements();) {
                String alias = aliases.nextElement();
                if (trust.isKeyEntry(alias)) { throw new GeneralSecurityException("Client truststore must contain public certificates only"); }
                ((X509Certificate) trust.getCertificate(alias)).checkValidity();
            }
            TrustManagerFactory manager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            manager.init(trust);
            SSLContext context = SSLContext.getInstance(PROTOCOL);
            context.init(null, manager.getTrustManagers(), new SecureRandom());
            return context;
        } finally { clear(password); }
    }

    public static SSLContext serverFromEnvironment(Map<String, String> environment)
            throws IOException, GeneralSecurityException {
        return server(Path.of(required(environment, "SEATSYNC_TLS_KEYSTORE")),
            required(environment, "SEATSYNC_TLS_KEYSTORE_PASSWORD").toCharArray());
    }

    public static SSLContext clientFromEnvironment(Map<String, String> environment)
            throws IOException, GeneralSecurityException {
        return client(Path.of(required(environment, "SEATSYNC_TLS_TRUSTSTORE")),
            required(environment, "SEATSYNC_TLS_TRUSTSTORE_PASSWORD").toCharArray());
    }

    public static SSLEngine serverEngine(SSLContext context) {
        SSLEngine engine = Objects.requireNonNull(context).createSSLEngine();
        engine.setUseClientMode(false);
        engine.setEnabledProtocols(new String[] {PROTOCOL});
        return engine;
    }

    public static SSLEngine clientEngine(SSLContext context, String host, int port) {
        if (host == null || host.isBlank()) { throw new IllegalArgumentException("TLS peer host is required"); }
        SSLEngine engine = Objects.requireNonNull(context).createSSLEngine(host, port);
        engine.setUseClientMode(true);
        engine.setSSLParameters(clientParameters());
        return engine;
    }

    public static SSLParameters clientParameters() {
        SSLParameters parameters = new SSLParameters();
        parameters.setProtocols(new String[] {PROTOCOL});
        // HTTPS is the JSSE hostname/IP certificate identity algorithm, also used for this custom TCP protocol.
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        return parameters;
    }

    private static KeyStore load(Path path, char[] password) throws IOException, GeneralSecurityException {
        if (path == null || password == null || password.length == 0 || !Files.isRegularFile(path)) {
            throw new GeneralSecurityException("A PKCS12 file and non-empty password are required");
        }
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream stream = Files.newInputStream(path)) { store.load(stream, password); }
        return store;
    }
    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) { throw new IllegalArgumentException("Missing TLS setting: " + name); }
        return value;
    }
    private static void clear(char[] password) { if (password != null) { Arrays.fill(password, '\0'); } }
}
