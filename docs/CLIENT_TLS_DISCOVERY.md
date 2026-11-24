# Desktop TLS and server discovery

The login screen can discover SeatSync servers over UDP on the LAN's discovery
port (`5051`) or use a manually entered host and port. Discovery reports the TCP
and TLS ports and whether TLS is mandatory. A server marked TLS-required selects
the TLS port and prevents a plaintext login. Discovery is a convenience only;
its response does not establish certificate trust.

To connect with TLS, configure a PKCS12 truststore containing the server's
trusted public certificate:

```sh
export SEATSYNC_TLS_TRUSTSTORE=/path/to/seatsync-trust.p12
export SEATSYNC_TLS_TRUSTSTORE_PASSWORD='use-a-local-secret'
```

Select **Require TLS 1.3** on the login screen, or select a discovered server
that requires TLS. `TlsContextFactory` loads the explicit truststore;
`TrustedTlsSocket` performs a TLS 1.3 handshake with certificate-chain and
hostname/IP verification. The client never retries a failed secure connection
using plaintext. An untrusted, expired, or hostname-mismatched certificate
rejects the connection. For TLS-enabled service, the server must be configured
with its own PKCS12 keystore according to `TlsServerConfiguration`.

The UI supports manual address entry as a fallback if UDP broadcast is blocked
or the server is on another subnet. Discovery is limited to the local broadcast
domain. Default plaintext is intended only for the loopback development server;
do not use it over an untrusted network.

Verification:

```sh
mvn -Dtest=TcpClientTlsTest,NioTlsIntegrationTest,UdpDiscoveryTest test
```

The TLS client tests establish a trusted TLS connection, reject an untrusted
certificate, and verify that a TLS client does not downgrade when pointed at a
plaintext port. The client clears its handshake-only socket read timeout after
the TLS 1.3 handshake, so an otherwise idle event subscription is not closed
after the connect timeout expires.

NIO discovery advertises the TLS port and requirement state from the active
server configuration. For TLS-required mode, the advertised TCP endpoint is the
TLS listener; optional TLS mode advertises both its plaintext and TLS endpoints.
These cases are covered by `NioTlsIntegrationTest`.
