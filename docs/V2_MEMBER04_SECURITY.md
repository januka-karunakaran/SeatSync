# Member 04: v2 security, admin policy and TLS configuration

This implementation follows the user's final allocation: U12 TLS config, U02 RBAC/admin,
and U15 security regression. The older V2_PR_TRACKER.md has different numbering.

Branch: upgrade/04-security-admin. The completed M3 dependency branch
origin/upgrade/05-seat-holds (including U08 waitlist) was merged locally. This dependency
merge is separate from the uncommitted M4 changes. No remote PR or merge is claimed.

## Implemented behavior

| Area | Behavior |
| --- | --- |
| Identity | Immutable role on User; UserRepository.findById resolves session identity. Legacy accounts default CUSTOMER. OPERATOR has no admin privilege. |
| Credentials | Existing PBKDF2 retained. Separate environment-supplied admin password; no hardcoded production credentials. |
| Permissions | SecurityRequestDispatcher gates admin/telemetry routes. AdminRequestDispatcher/AdminService independently recheck permissions. Forged actor/role rejected. |
| Catalog | ADMIN_CREATE_MOVIE / ADMIN_CREATE_SHOW link movies to future, nonoverlapping show windows. ADMIN_ADD_SHOW remains compatible. Metadata/layout validation, 120-seat cap, paged GET_MOVIES. |
| Lifecycle | ADMIN_DISABLE_SHOW atomically removes holds/offers/waitlist, hides the show, blocks new bookings, and preserves existing confirmed reservations. |
| Booking projection | ADMIN_LIST_BOOKINGS returns an immutable, sorted per-show active-booking page, never a second booking store. |
| Overrides | Atomic BOOKED/HELD/AVAILABLE overrides maintain reservation/hold indexes. Allocations require a free seat; freeing cannot skip queued users. |
| Holds | ADMIN_CANCEL_HOLD resolves stored owner and invokes M3 atomic release, preserving expiry/FIFO behavior. No duplicate booking state. |
| Audit | Bounded immutable metadata with actor/action/timestamp/outcome/requestId; no raw payloads, passwords, tokens or reasons. |
| Telemetry | ADMIN-only GET_NETWORK_METRICS / GET_SERVER_METRICS use the real server tracker. |
| TLS | Explicit PKCS12 key/trust loading, certificate validity, TLS 1.3-only engines, SAN identity checks, verified client socket helper. |

The user explicitly authorized shared protocol/backend extensions during implementation.
New DTOs/commands and additive LoginOkReply.role/ShowUpdatedEvent.enabled retain old
constructors and legacy deserialization. ShowDetails remains unchanged; linked show
metadata comes from the stored movie, rather than trusting a second supplied title.
AuditLogEntry.detail carries
`outcome=...;requestId=...`. ADMIN_GET_AUDIT_LOGS accepts integer limits 1-20;
the audit retains 1000 entries. Only bounded identifiers are retained.

Outside security/tests/docs/scripts, changes cover shared contracts/DTOs, server bootstrap,
the TLS engine policy, and the user-authorized atomic admin operations in M3's existing
inventory. No second seat store or synchronization lock was introduced.

## Development TLS setup (PowerShell)

Supply your own passwords. This helper avoids putting values in shell history:

```powershell
function Read-SeatSyncSecret([string]$Prompt) {
    $secret = Read-Host $Prompt -AsSecureString
    [System.Net.NetworkCredential]::new('', $secret).Password
}
$env:SEATSYNC_DEMO_PASSWORD = Read-SeatSyncSecret 'Customer demo password'
$env:SEATSYNC_ADMIN_PASSWORD = Read-SeatSyncSecret 'Separate admin password (12+ chars)'
$env:SEATSYNC_TLS_KEYSTORE_PASSWORD = Read-SeatSyncSecret 'Private PKCS12 password (12+ chars)'
$env:SEATSYNC_TLS_TRUSTSTORE_PASSWORD = Read-SeatSyncSecret 'Public truststore password (12+ chars)'
./scripts/generate-dev-tls.ps1
$env:SEATSYNC_TLS_KEYSTORE = Join-Path $PWD '.local/tls/server.p12'
$env:SEATSYNC_TLS_TRUSTSTORE = Join-Path $PWD '.local/tls/trust.p12'
$env:SEATSYNC_TLS_MODE = 'REQUIRED'
$env:SEATSYNC_TLS_PORT = '8443'
mvn compile exec:java@server
```

The script generates a 30-day certificate with SAN dns:localhost and ip:127.0.0.1,
exports its public certificate and builds a public-only PKCS12 truststore. It refuses
overwrites. Password values are read by keytool from the environment, not arguments.
Private material and .local are Git-ignored. Protect server.p12; share only the public
truststore/certificate after verifying its SHA-256 fingerprint with the server owner
over a trusted channel. Regenerate and redistribute trust before expiry. LAN deployment
requires SANs for the actual hostname/IP; localhost certificates do not cover arbitrary IPs.

For the existing plaintext GUI, choose `$env:SEATSYNC_TLS_MODE = 'DISABLED'` on
127.0.0.1. This is labeled unencrypted development only. No failed TLS connection
automatically retries plaintext.

## Handoff APIs

```java
// M2: retain the NIO transport, using M4's policy and credentials.
SSLContext serverContext = TlsContextFactory.serverFromEnvironment(System.getenv());
SSLEngine engine = TlsContextFactory.serverEngine(serverContext);

// M5: call from its background transport/reconnect adapter, never Swing's EDT.
SSLContext clientContext = TlsContextFactory.clientFromEnvironment(System.getenv());
SSLSocket socket = TrustedTlsSocket.connect(clientContext, host, port, 5000);

// M1: use the stored ticket owner, not any request-supplied owner.
boolean allowed = authorization.canReadOwnedResource(context, storedTicketOwnerId);
```

Every new client connection must use the trusted helper or clientEngine/clientParameters.
SSLContext alone does not activate hostname validation. Reference:
[JDK 21 JSSE guide](https://docs.oracle.com/en/java/javase/21/security/java-secure-socket-extension-jsse-reference-guide.html).

## Remaining cross-member acceptance gates

| Item | Required next step |
| --- | --- |
| Show lifecycle push | Source emits post-commit ShowUpdatedEvent with enabled/version. Bootstrap fans out public catalog changes to authenticated clients. M1 U06 can attach its subscription-aware EventBus instead. |
| Role rendering | M5 reads the additive LoginOkReply.role field. UI visibility remains separate from server authorization. |
| M5 U14 | Adopt the trusted socket in the actual GUI/discovery/reconnect transport. TcpClient is currently plaintext. |
| Final U15 | M1 ticket endpoints and M5 U14 required for cross-user ticket retrieval and full client security tests. Policy tests alone do not prove absent endpoints. |

U12 and U02 are implemented, including the explicitly authorized protocol/backend
amendment. Backend U15 tests work; final cross-member acceptance remains pending
M1 ticket endpoints and M5 U14.

## Verification and oral demo

```powershell
mvn "-Dtest=AdminSecurityTest,SecurityTlsRegressionTest" test
mvn test
mvn -DskipTests package
git diff --check
```

Verification here uses `-Dmaven.repo.local=C:\Users\Jogeesan\.m2\repository`
because the sandbox default Maven home resolves differently. No pom/global setting changed.
Final clean full run: **104 tests, 0 failures, 0 errors, 0 skipped**. The final JAR
and runtime dependency package also built successfully with tests skipped only at
the packaging step, after the clean full test run. Evidence is extracted from actual
Surefire XML in [admin evidence](evidence/member04-admin-tests.txt) and
[TLS evidence](evidence/member04-tls-tests.txt).
The certificate script passed a Windows PowerShell smoke
test with temporary random passwords; generated private smoke-test files were removed.

Coverage: wrong/valid admin login, anonymous/customer denial, forged identity, metadata
validation, movie/show creation, duplicate and overlapping schedules, disable/confirm
races, override/book races, FIFO invariants, paged admin projections, old wire messages,
hold release, bounded/redacted audit, owner policy, real NIO
TLS 1.3, untrusted/wrong-host/expired certificates, absent/empty/private-key truststores,
bad store passwords, TLS 1.2 rejection, malformed/oversized decrypted frames, plaintext
refusal on the secure port. CI certificates are generated under @TempDir.

The two evidence files can be included in the report; regenerate after future changes.
For the two-minute oral: explain session roles, show customer denial/admin success,
then demonstrate wrong-certificate rejection. Distinguish PBKDF2 verifier protection
from TLS traffic encryption and server identity validation. Never screenshot secrets.
