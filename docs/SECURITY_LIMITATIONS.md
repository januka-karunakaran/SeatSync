# Authentication and security limitations

This is a localhost/trusted-LAN demonstration, not production authentication.
Set `SEATSYNC_DEMO_PASSWORD` before starting the server; the same locally chosen
password is used by the five demo users `alice`, `bob`, `carol`, `dave`, and `erin`.
For example, for local testing only:

```sh
export SEATSYNC_DEMO_PASSWORD='password123'
mvn exec:java@server
```

In PowerShell use `$env:SEATSYNC_DEMO_PASSWORD = 'password123'`. Never use these
demo credentials for real accounts. The server requires this environment variable
and creates fresh in-memory PBKDF2-HMAC-SHA256 password hashes and random
per-user salts at startup; accounts and bookings do not persist across restarts.
Environment variables and Java strings can remain in process memory.

Authentication verifies credentials; authorization checks what the authenticated
session may do. Requests cannot supply the acting user ID. Cancellation is checked
against the booking owner; the eventual reservation engine must check ownership
atomically within its own cancellation transaction. The current bootstrap uses the
temporary mock booking dispatcher behind the security gate, not the future booking
engine. Other code must not expose the mock dispatcher directly.

**There is no TLS.** Passwords, requests, and replies travel over unencrypted TCP.
Hashing protects stored password verifiers, not data in transit. Anyone who can
observe or modify traffic can obtain credentials or impersonate users. Do not use
this server over an untrusted network. The demo has no production account storage,
password reset, lockout, or comprehensive rate limiting.

The security gate accepts canonical seat IDs `A1`-`E8`, booking IDs beginning
`BKG-` followed by 1-64 letters, digits or hyphens, and request IDs of 1-128
letters, digits, `.`, `_` or `-`. It rejects unexpected JSON fields, including
client-supplied user IDs. Identifiers must be adjusted in coordination with the
booking and client owners if their contracts change.

Member 5 contribution: password hashing and demo repository, credential
verification, session-based authorization and request validation, plus negative
security tests. Run `mvn clean test` to reproduce the tests. Socket-level security
tests against the final reservation engine are a separate integration stage.
