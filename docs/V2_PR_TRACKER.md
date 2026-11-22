# SeatSync 2.0 PR Tracker & Implementation Roadmap

This tracker defines the official pull request sequence for the SeatSync 2.0 upgrade sprint, spanning **PR U00 through PR U17**. Each PR has a designated owner, reviewer, branch convention, dependency tree, and acceptance criteria.

---

## 1. Team Ownership & Responsibilities

| Role | Member | Primary Focus Area | Assigned PRs |
| :--- | :--- | :--- | :--- |
| **M1** | Member 01 | Delivery Lead, Digital Tickets, Receipts, Paged History, Quality Gate | U07, U08, U16, U17 |
| **M2** | Member 02 | Upgrade Architecture, Java NIO Server, UDP Beacon, Metrics, Pub/Sub | U00, U01, U09, U10 |
| **M3** | Member 03 | Multi-Show Catalog, Two-Phase Holds (TTL), FIFO Waitlists | U02, U03, U04 |
| **M4** | Member 04 | Role-Based Access Control (RBAC), Admin Governance, Audit Logging, TLS | U05, U06, U11 |
| **M5** | Member 05 | Swing Client V2: Catalog Browser, Hold Countdown, Waitlist Dialogs, Admin Panel | U12, U13, U14, U15 |

---

## 2. Master PR Dependency Graph

```
[U00: V2 Contracts & Architecture Foundation] (M2)
  │
  ├──► [U01: High-Throughput NIO Refinements & Pub/Sub Dispatcher] (M2)
  │      │
  │      ├──► [U02: Multi-Show Catalog Service] (M3)
  │      │      │
  │      │      └──► [U03: Two-Phase Seat Hold Engine & TTL Reaper] (M3)
  │      │             │
  │      │             ├──► [U04: FIFO Waitlist & Auto-Promotion Engine] (M3)
  │      │             │      │
  │      │             │      └──► [U08: Idempotent Booking Confirmation] (M1)
  │      │             │
  │      │             └──► [U07: Digital Ticket & PDF/Receipt Generation] (M1)
  │      │                    │
  │      │                    └──► [U16: Paged Booking History & Export] (M1)
  │      │
  │      ├──► [U05: RBAC & User Session Role Enforcement] (M4)
  │      │      │
  │      │      └──► [U06: Admin Governance & Structured Audit Logging] (M4)
  │      │
  │      ├──► [U09: UDP LAN Discovery Beacon Service] (M2)
  │      │
  │      ├──► [U10: Real-Time Server Telemetry & Metrics Engine] (M2)
  │      │
  │      └──► [U11: TLS 1.3 Transport Security Handshake] (M4)
  │
  └──► Client UI Work (Consumes Backend Features):
         ├──► [U12: Swing Catalog Browser & Auditorium Grid] (M5)  [Depends on U02]
         ├──► [U13: Swing 120s Hold Countdown & Booking Flow] (M5) [Depends on U03, U07]
         ├──► [U14: Swing Waitlist Status & Promotion Alerts] (M5) [Depends on U04]
         └──► [U15: Swing Admin Dashboard & Audit Log Viewer] (M5) [Depends on U06, U10]
                │
                └──► [U17: V2 Release Hardening, E2E Verification & Freeze] (Joint M1/M2)
```

---

## 3. Pull Request Details (U00 – U17)

### Phase 1: Foundation & Infrastructure (Days 1–2)

#### PR U00: V2 Contracts, DTOs & Architectural Foundation
- **Branch**: `upgrade/00-v2-contracts`
- **Owner**: M2 | **Reviewer**: M1 | **Target**: `main`
- **Scope**: Add 5 architecture docs (`V2_SCOPE.md`, `V2_ARCHITECTURE.md`, `V2_PROTOCOL.md`, `V2_TEST_MATRIX.md`, `V2_PR_TRACKER.md`), 18 domain models, 9 service contracts, 43 Jackson message records, and contract test suite `V2ProtocolContractTest`.
- **Status**: **READY FOR REVIEW / COMPLETED IN THIS SPRINT**

#### PR U01: High-Throughput NIO Server Refinements & Pub/Sub Dispatcher
- **Branch**: `feature/u01-nio-pubsub`
- **Owner**: M2 | **Reviewer**: M3 | **Target**: `main`
- **Prerequisites**: U00 merged
- **Scope**: Implement `EventSubscriptionService`, selector event broadcast channels, lock-free client session registry, OP_WRITE backpressure queue.
- **Tests**: Multi-client event subscription tests, 100 concurrent broadcast delivery tests.

#### PR U02: In-Memory Multi-Show Catalog & Layout Management
- **Branch**: `feature/u02-multi-show-catalog`
- **Owner**: M3 | **Reviewer**: M2 | **Target**: `main`
- **Prerequisites**: U00, U01 merged
- **Scope**: Implement `CatalogService`, dynamic show registration, multi-auditorium seat maps, tiered pricing calculations (`STANDARD`, `VIP`, `BALCONY`).
- **Tests**: Unit tests for seat tiering and layout generation; integration tests for `GetShowsRequest` and `GetShowDetailsRequest`.

#### PR U03: Two-Phase Seat Hold Engine & TTL Background Reaper
- **Branch**: `feature/u03-seat-holds-ttl`
- **Owner**: M3 | **Reviewer**: M4 | **Target**: `main`
- **Prerequisites**: U02 merged
- **Scope**: Implement `HoldService`, atomic seat hold acquisition, 120s TTL countdown, `ScheduledExecutorService` reaper for expired holds, seat release broadcasts.
- **Tests**: High-concurrency seat hold race condition tests (50 threads on same seat); deterministic clock expiration tests.

---

### Phase 2: Core Booking Services & Governance (Days 2–3)

#### PR U04: FIFO Waitlist & Automatic Hold Promotion Engine
- **Branch**: `feature/u04-waitlist-engine`
- **Owner**: M3 | **Reviewer**: M1 | **Target**: `main`
- **Prerequisites**: U03 merged
- **Scope**: Implement `WaitlistService`, per-show FIFO waitlist queues, auto-promotion of head-of-queue when holds expire or bookings are cancelled.
- **Tests**: Concurrent waitlist join/leave queue order verification; auto-promotion event delivery tests.

#### PR U05: Role-Based Access Control (RBAC) & Session Role Resolution
- **Branch**: `feature/u05-rbac-enforcement`
- **Owner**: M4 | **Reviewer**: M2 | **Target**: `main`
- **Prerequisites**: U01 merged
- **Scope**: Implement `UserRole` resolution on login, role permission guards in `RequestDispatcher`, rejection of privileged actions by standard users.
- **Tests**: Security tests verifying regular users receive `FORBIDDEN_ROLE` when invoking admin endpoints.

#### PR U06: Admin Governance & Structured Audit Logging
- **Branch**: `feature/u06-admin-governance`
- **Owner**: M4 | **Reviewer**: M3 | **Target**: `main`
- **Prerequisites**: U05 merged
- **Scope**: Implement `AdminManagementService`, emergency hold cancellation, seat maintenance overrides, append-only in-memory `AuditLogEntry` store.
- **Tests**: Admin force-release tests; audit trail verification for all state-changing operations.

#### PR U07: Digital Ticket Generation & Verification Token
- **Branch**: `feature/u07-digital-tickets`
- **Owner**: M1 | **Reviewer**: M4 | **Target**: `main`
- **Prerequisites**: U03 merged
- **Scope**: Implement `TicketService`, digital ticket generation upon hold confirmation, SHA-256 verification hash, tamper detection.
- **Tests**: Ticket retrieval tests, invalid ticket ID handling, verification token validation.

#### PR U08: Idempotent Booking Confirmation Engine
- **Branch**: `feature/u08-booking-idempotency`
- **Owner**: M1 | **Reviewer**: M3 | **Target**: `main`
- **Prerequisites**: U03, U04 merged
- **Scope**: Implement idempotency key cache for `ConfirmHoldRequest` and `BOOK_SEAT` to prevent duplicate billing or double-confirmation under network retries.
- **Tests**: Repeated request replay tests with identical `requestId` and payment token.

---

### Phase 3: Networking, Discovery & UI Integration (Days 3–4)

#### PR U09: UDP Local Area Network Discovery Beacon
- **Branch**: `feature/u09-udp-discovery`
- **Owner**: M2 | **Reviewer**: M5 | **Target**: `main`
- **Prerequisites**: U01 merged
- **Scope**: Implement UDP broadcast sender on server (`UdpBeaconPayload` on port 8888 every 5s) and UDP receiver in client network layer for zero-config server discovery.
- **Tests**: UDP broadcast packet serialization and loopback socket receipt tests.

#### PR U10: Real-Time Server Telemetry & Metrics Engine
- **Branch**: `feature/u10-server-metrics`
- **Owner**: M2 | **Reviewer**: M4 | **Target**: `main`
- **Prerequisites**: U01 merged
- **Scope**: Implement `MetricsService`, lock-free atomic counters for active connections, requests/sec, booking success rate, periodic metrics broadcast.
- **Tests**: Metrics accuracy tests under concurrent synthetic load.

#### PR U11: TLS 1.3 Transport Security Mode
- **Branch**: `feature/u11-tls-transport`
- **Owner**: M4 | **Reviewer**: M2 | **Target**: `main`
- **Prerequisites**: U01 merged
- **Scope**: Implement dual port support (plain 8080/5050, TLS 8443) using Java NIO `SSLEngine` and pluggable key/trust store configuration.
- **Tests**: TLS 1.3 handshake integration tests; plaintext rejection on secure port tests.

#### PR U12: Swing Client V2: Catalog Browser & Auditorium Grid
- **Branch**: `feature/u12-swing-catalog`
- **Owner**: M5 | **Reviewer**: M1 | **Target**: `main`
- **Prerequisites**: U02 merged
- **Scope**: Update Swing client with multi-show selection dropdown, interactive seat grid with tiered color coding, dynamic capacity indicators.
- **Tests**: UI unit tests; manual verification of show switching.

#### PR U13: Swing Client V2: 120s Hold Countdown & Booking Flow
- **Branch**: `feature/u13-swing-holds`
- **Owner**: M5 | **Reviewer**: M3 | **Target**: `main`
- **Prerequisites**: U03, U07, U12 merged
- **Scope**: Interactive seat click-to-hold, visual countdown timer (120s bar), payment confirmation modal, digital ticket dialog.
- **Tests**: Visual verification of hold timer expiration and seat release in UI.

#### PR U14: Swing Client V2: Waitlist Dialog & Promotion Notifications
- **Branch**: `feature/u14-swing-waitlist`
- **Owner**: M5 | **Reviewer**: M3 | **Target**: `main`
- **Prerequisites**: U04, U13 merged
- **Scope**: Waitlist join dialog on sold-out shows, real-time queue position display, pop-up promotion alert with one-click hold acceptance.
- **Tests**: Waitlist join/leave UI interaction tests.

#### PR U15: Swing Client V2: Admin Dashboard & Telemetry Visualizer
- **Branch**: `feature/u15-swing-admin-ui`
- **Owner**: M5 | **Reviewer**: M4 | **Target**: `main`
- **Prerequisites**: U06, U10, U12 merged
- **Scope**: Admin management tab (visible only for `ADMIN` role), show creation form, maintenance toggles, live audit log table, server telemetry charts.
- **Tests**: Role-based panel visibility tests; audit log refresh tests.

#### PR U16: Paged Booking History & Receipt Export
- **Branch**: `feature/u16-booking-history`
- **Owner**: M1 | **Reviewer**: M5 | **Target**: `main`
- **Prerequisites**: U07, U08 merged
- **Scope**: Implement `HistoryService` bounded pagination backend and Swing user booking history table with receipt export to text/file.
- **Tests**: Pagination boundary tests (page size 10, 50, invalid sizes); export format verification.

---

### Phase 4: Final Verification & Code Freeze (Day 4)

#### PR U17: Release Hardening, End-to-End Test Suite & Moodle Freeze
- **Branch**: `release/u17-final-hardening`
- **Owner**: Joint M1 (Lead) & M2 (NIO/Arch) | **Reviewer**: All Members | **Target**: `main`
- **Prerequisites**: All prior PRs merged (U00–U16)
- **Scope**: Complete end-to-end multi-client integration test, final documentation audit, stress testing under 200 concurrent simulated users, package build verification, clean tag `v2.0.0-final`.
- **Tests**: 100% test pass on CI across full suite (L1–L5).
