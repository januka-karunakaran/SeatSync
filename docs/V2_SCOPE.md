# SeatSync 2.0 Scope and System Evolution Specification

## 1. Executive Summary & Upgrade Context

SeatSync 2.0 is an additive, backwards-compatible architectural evolution of the real-time ticket booking platform. Having successfully established Java NIO selector multiplexing, thread-safe session tracking, worker request dispatching, authentication, and simple single-show booking in v1.0, SeatSync 2.0 expands the system into a resilient, production-grade distributed event platform without rewriting core foundations or introducing extraneous framework bloat.

### Non-Negotiable Tenets
1. **Additive Evolution, Not Greenfield:** Existing v1 message contracts, wire framing, and concurrency guarantees remain fully supported.
2. **Framework Freedom:** Pure Java 21 LTS with Maven, Jackson JSON, and JUnit 5. No Spring Boot, no external database dependencies, and no third-party cloud payment APIs.
3. **Pure Session Identity:** Client request payloads never specify or dictate user identity. Identity is strictly derived on the server from authenticated `RequestContext` sessions.
4. **Deterministic Concurrency:** In-memory transactional models, thread pools, and lock management with monotonic versioning.

---

## 2. The Ten Core Features of SeatSync 2.0

### Feature 1: Multi-Show Catalog & Dynamic Auditorium Layouts
- **Description:** Expands beyond the single `SHOW-01` constraint to support multiple simultaneous productions, venues, showtimes, and multi-tier auditorium layouts (Standard, VIP, Premium, Accessible).
- **Core Models:** `ShowSummary`, `ShowDetails`, `AuditoriumLayout`, `SeatTier`, `TierType`.
- **Contracts:** `CatalogService.getShows()`, `CatalogService.getShowDetails(showId)`.

### Feature 2: Two-Phase Seat Booking (Hold & Confirm with TTL)
- **Description:** Prevents booking cart collisions through temporary, exclusive seat holds with a configurable Time-To-Live (TTL; default 120 seconds). Users hold seats, review pricing, and confirm purchase using an idempotency key.
- **Core Models:** `SeatHold`, `HoldResult`, `ReleaseResult`, `SeatStatus.HELD`.
- **Contracts:** `HoldService.holdSeat()`, `HoldService.releaseHold()`, `HoldService.confirmHold()`.

### Feature 3: FIFO Waitlist Management per Show
- **Description:** When a show is fully booked or all remaining seats are held, clients can join a first-in, first-out (FIFO) waitlist queue. If a hold expires or a booking is cancelled, the head of the waitlist is automatically promoted to an exclusive hold.
- **Core Models:** `WaitlistEntry`, `WaitlistResult`.
- **Contracts:** `WaitlistService.joinWaitlist()`, `WaitlistService.leaveWaitlist()`, `WaitlistService.getWaitlistStatus()`.

### Feature 4: Digital Ticket Issuance & Cryptographic Checksums
- **Description:** Upon booking confirmation, the server issues a digital ticket record containing booking metadata and an immutable SHA-256 verification hash to prevent fraudulent duplication or alteration.
- **Core Models:** `Ticket`.
- **Contracts:** `TicketService.issueTicket()`, `TicketService.getTicket()`, `TicketService.verifyTicket()`.

### Feature 5: Paginated Booking History Archive
- **Description:** Allows authenticated users to query their historical bookings and ticket archives with strict bounded pagination (default 10 items, maximum 50 items per page) to prevent server memory exhaustion.
- **Core Models:** `BookingHistoryItem`, `PagedResult<T>`.
- **Contracts:** `HistoryService.getBookingHistory(userId, pageNumber, pageSize)`.

### Feature 6: Role-Based Access Control (RBAC) & Admin Governance
- **Description:** Introduces explicit user roles (`CUSTOMER`, `OPERATOR`, `ADMIN`). Administrative actions (adding shows, overriding seat status for maintenance, emergency hold cancellation, and audit log inspection) require verified `ADMIN` privileges.
- **Core Models:** `UserRole`, `AuditLogEntry`.
- **Contracts:** `AdminManagementService.addShow()`, `updateSeatStatus()`, `cancelHold()`, `getAuditLogs()`.

### Feature 7: UDP LAN Server Discovery Beacon
- **Description:** Implements zero-configuration local area network discovery using a lightweight UDP broadcast/multicast beacon (default port 8888). Clients automatically detect running SeatSync servers, active connection load, and available TCP/TLS ports.
- **Core Models:** `UdpBeaconPayload`.
- **Transport:** Java NIO `DatagramChannel` broadcasting periodic heartbeats.

### Feature 8: Real-Time Telemetry, Metrics & Health Monitoring
- **Description:** Provides a low-overhead telemetry collector tracking active connections, throughput, hold volume, waitlist queue depth, p95 latency percentiles, and error counters.
- **Core Models:** `ServerMetricSnapshot`.
- **Contracts:** `MetricsService.getMetricsSnapshot()`, `MetricsService.recordRequest()`.

### Feature 9: Selective Pub/Sub Event Filtering
- **Description:** Enhances real-time server push by allowing clients to subscribe specifically to event channels for the shows they are currently viewing, preventing network saturation from broadcasting global seat changes to every client.
- **Core Models:** `SeatHeldEvent`, `SeatReleasedEvent`, `WaitlistPromotedEvent`, `ShowUpdatedEvent`, `MetricsUpdatedEvent`.
- **Contracts:** `EventSubscriptionService.subscribe()`, `EventSubscriptionService.unsubscribe()`.

### Feature 10: Configurable TLS Secure Transport Mode
- **Description:** Adds an optional TLS/SSL secure transport layer (`TlsConfigMode`: `DISABLED`, `OPTIONAL`, `REQUIRED`) on port 8443 alongside plain TCP port 8080. Protects credentials and transactions without sacrificing plain TCP backwards compatibility.
- **Core Models:** `TlsConfigMode`.
- **Contracts:** Java NIO `SSLEngine` integration and client-side SSL socket factories.

---

## 3. Explicit Non-Goals (Scope Boundaries)

1. **No External Database Systems:** SeatSync 2.0 remains 100% in-memory with thread-safe data structures. No PostgreSQL, MySQL, MongoDB, or Redis dependencies are permitted.
2. **No Third-Party Payment Gateways:** Real banking transactions (Stripe, PayPal) are outside project scope. Financial transactions are modeled via integer cents and client-supplied `idempotencyKey` strings.
3. **No Heavy Web Frameworks:** The server remains a pure Java NIO selector engine, and the client remains a desktop Swing application. No Spring Boot, Quarkus, or React.
4. **No Premature Distributed Cluster Consensus:** State coordination is single-server in-memory with concurrent locking, not Raft/Paxos.

---

## 4. Backwards Compatibility Matrix

| Feature / Property | Version 1.0 Behavior | Version 2.0 Behavior | Compatibility Status |
| :--- | :--- | :--- | :--- |
| **Transport Encoding** | UTF-8 JSON lines (`\n`) | UTF-8 JSON lines (`\n`) | 100% Identical |
| **Frame Size Ceiling** | 16 KiB (16,384 bytes) | 16 KiB (16,384 bytes) | 100% Identical |
| **Correlation ID** | Required on all requests/replies | Required on all requests/replies | 100% Identical |
| **Default Plain Port** | TCP 8080 | TCP 8080 (TLS on 8443) | 100% Backwards Compatible |
| **V1 Request Types** | `LOGIN`, `GET_SEATS`, `BOOK_SEAT`, `CANCEL_BOOKING`, `LOGOUT` | Supported unchanged | 100% Backwards Compatible |
| **V1 Direct Booking** | Direct reservation to `BOOKED` | Direct reservation supported; two-phase hold optional | Fully Preserved |
| **Single Show Default** | `SHOW-01` | `SHOW-01` remains the default canonical show | Fully Preserved |
| **User Identity** | Derived from server session | Derived from server session | 100% Preserved |
