# Security modes and limitations (v2)

The v2 server uses the real reservation/hold/waitlist engine behind a worker-level
security gate. Roles are immutable server-side account data. Only an ADMIN resolved
by authenticated user ID can access admin commands and telemetry. Names containing
admin and client-supplied actor/userId/role fields never grant access. Legacy accounts
remain CUSTOMER; OPERATOR grants no admin permissions.

SEATSYNC_DEMO_PASSWORD configures customers alice, bob, carol, dave and erin.
A distinct SEATSYNC_ADMIN_PASSWORD (12-1024 characters) enables username admin.
Absent this variable, no admin account is created. PBKDF2 with unique salts remains
in use. Character arrays are cleared; environment values and Java Strings can remain
in process memory. Never use demonstration credentials for real accounts.

Default DISABLED mode is explicitly labeled PLAINTEXT LOCAL DEVELOPMENT ONLY and
requires a loopback bind address. REQUIRED mode opens only the TLS 1.3 NIO listener,
loads explicit PKCS12 key material, and fails startup on invalid configuration.
There is no automatic downgrade. TlsContextFactory/TrustedTlsSocket require an
explicit public truststore and verify the server certificate's hostname/IP SAN.
An unknown certificate, expired certificate or wrong hostname fails validation.

The existing Swing TcpClient is still plaintext; M5 U14 must adopt the trusted socket
provider. Switching the server to REQUIRED does not automatically upgrade the GUI.
UDP discovery hints do not establish trust. The dev certificate is localhost-only,
expires in 30 days, and must be verified by fingerprint over a trusted channel.
Protect server private key filesystem permissions. No private key is committed.

Audit stores at most 1000 in-memory metadata records and returns at most 20 per query.
It never retains raw requests, password values, token bodies or cancellation reasons.
This is not durable/tamper-resistant logging. Use opaque correlation IDs rather than
secrets in identifier fields. Accounts, audit and booking state reset on restart.

The user-authorized protocol amendment adds movie creation, linked show creation,
show disabling, paged admin booking lists and atomic seat overrides. These use the
existing inventory lock and source of truth. Disabling a show revokes active holds
and waitlist offers, hides it from browsing and blocks new allocations; confirmed
bookings remain for inspection/owner cancellation. AVAILABLE seat overrides reject
nonempty waitlists; normal cancellation preserves FIFO promotion. Catalog limits
are 10 shows/1000 movies; movie pages contain at most 5 entries and admin booking
pages at most 20. Pagination is point-in-time per request, not a cross-page transaction.

M1 ticket endpoints must enforce the provided stored-owner policy; complete ticket
and M5 U14 client integration tests remain pending. Login replies now expose the
server-derived role for UI use. See [Member 4 implementation and handoff](V2_MEMBER04_SECURITY.md).

There is no production provisioning, password reset, global login throttling,
automatic certificate renewal, revocation checking service, mTLS or durable audit.
