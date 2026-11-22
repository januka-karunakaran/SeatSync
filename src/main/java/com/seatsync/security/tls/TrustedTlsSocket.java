package com.seatsync.security.tls;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Objects;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

/** M5 transport building block: verified TLS 1.3 connection; any failure closes the socket, never downgrades. */
public final class TrustedTlsSocket {
    private TrustedTlsSocket() {}

    public static SSLSocket connect(SSLContext context, String host, int port, int timeoutMillis) throws IOException {
        Objects.requireNonNull(context);
        if (host == null || host.isBlank() || timeoutMillis < 1) { throw new IllegalArgumentException("Host and positive timeout required"); }
        Socket transport = new Socket();
        SSLSocket secure = null;
        try {
            transport.connect(new InetSocketAddress(host, port), timeoutMillis);
            secure = (SSLSocket) context.getSocketFactory().createSocket(transport, host, port, true);
            secure.setUseClientMode(true);
            secure.setSSLParameters(TlsContextFactory.clientParameters());
            secure.setSoTimeout(timeoutMillis);
            secure.startHandshake();
            return secure;
        } catch (IOException | RuntimeException failure) {
            try { if (secure != null) { secure.close(); } else { transport.close(); } }
            catch (IOException ignored) { /* Preserve the original handshake/identity failure. */ }
            throw failure;
        }
    }
}
