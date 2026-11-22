package com.seatsync.security.tls;

import com.seatsync.network.ServerConfig;
import java.io.IOException;
import java.net.InetAddress;
import java.security.GeneralSecurityException;
import java.util.Map;

/** Application bootstrap policy: TLS-required or explicitly labeled loopback development. */
public final class TlsServerConfiguration {
    private TlsServerConfiguration() {}
    public static ServerConfig fromEnvironment(String host, int plainPort, Map<String, String> environment)
            throws IOException, GeneralSecurityException {
        String mode = environment.getOrDefault("SEATSYNC_TLS_MODE", "DISABLED");
        if (mode.equals("DISABLED")) {
            if (!InetAddress.getByName(host).isLoopbackAddress()) {
                throw new IllegalArgumentException("Plaintext development mode requires a loopback bind address");
            }
            return ServerConfig.of(host, plainPort);
        }
        if (!mode.equals("REQUIRED")) { throw new IllegalArgumentException("TLS mode must be REQUIRED or DISABLED"); }
        int tlsPort;
        try { tlsPort = Integer.parseInt(environment.getOrDefault("SEATSYNC_TLS_PORT", "8443")); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("Invalid TLS port"); }
        if (tlsPort < 1 || tlsPort > 65535) { throw new IllegalArgumentException("Invalid TLS port"); }
        return ServerConfig.withTls(host, plainPort, tlsPort, TlsContextFactory.serverFromEnvironment(environment));
    }
}
