# SeatSync 2.0 Test Matrix & Verification Strategy

This document specifies the verification plan, test levels, pass criteria, and regression test strategy for SeatSync 2.0 across all ten target features.

---

## 1. Test Levels & Strategy Overview

| Level | Scope | Execution Framework | Primary Responsibility | Target Execution Time |
| :--- | :--- | :--- | :--- | :--- |
| **L1: Unit Tests** | Pure domain objects, records, state machines, deterministic clock logic | JUnit 5 | M1, M2, M3, M4 | < 2s |
| **L2: Contract Tests** | Jackson serialization / deserialization, UTF-8 framing, schema invariants | JUnit 5 + Jackson `ObjectMapper` | M2 (Contracts Lead) | < 3s |
| **L3: Concurrency Tests** | Atomic state transitions, race conditions, hold locks, FIFO queue thread safety | JUnit 5 + `CountDownLatch` + `ExecutorService` | M3, M4 | < 10s |
| **L4: Fault & Fuzz Tests** | Malformed JSON, partial lines, frame flooding (>16 KiB), abrupt client disconnects | Custom NIO harness + Raw Sockets | M2 (NIO Lead) | < 5s |
| **L5: End-to-End (E2E)** | Full server lifecycle, multi-client flows, UDP discovery, TLS negotiation | Socket clients + Swing headless UI | All Members | < 15s |

---

## 2. Feature-by-Feature Test Matrix

### F1: Multi-Show Catalog & Auditorium Layouts
- **L1 (Unit)**: `AuditoriumLayout` seat indexing; `ShowDetails` capacity calculation; tiered pricing lookup.
- **L2 (Contract)**: `GetShowsRequest` -> `ShowListReply`; `GetShowDetailsRequest` -> `ShowDetailsReply` with nested `SeatTier` array.
- **L3 (Concurrency)**: Concurrent read-only queries during dynamic show additions (`AdminAddShowRequest`).
- **Pass Criteria**:
  - Show list returns all active shows with correct available seat counters.
  - Non-existent show queries fail gracefully with `SHOW_NOT_FOUND`.
  - Seat tiers correctly reflect pricing (`STANDARD`, `VIP`, `BALCONY`).

---

### F2: Two-Phase Seat Reservation (Hold + Confirm) with TTL Expiration
- **L1 (Unit)**: `SeatHold` expiration check via `ClockService.instant()`; status transition from `AVAILABLE` -> `HELD` -> `BOOKED`.
- **L2 (Contract)**: `HoldSeatRequest`, `HoldOkReply`, `HoldRejectedReply`, `ConfirmHoldRequest`, `ConfirmOkReply`, `ReleaseHoldRequest`.
- **L3 (Concurrency)**: 50 concurrent threads attempting to hold the same seat `A1` simultaneously. Exactly 1 acquires hold; 49 receive `SEAT_ALREADY_HELD` or `SEAT_ALREADY_BOOKED`.
- **L4 (Fault/Reaper)**: Background TTL reaper task identifies expired holds and atomically frees the seat or triggers waitlist promotion.
- **Pass Criteria**:
  - Hold duration strictly enforced (default 120s; test clock advances 121s -> hold expires).
  - Only the user who holds the seat can confirm or release it; unauthorized confirmation rejected with `NOT_HOLD_OWNER`.
  - Confirming an expired hold fails with `HOLD_EXPIRED`.
  - Manual release immediately frees seat and broadcasts `SeatReleasedEvent`.

---

### F3: FIFO Waitlist & Auto-Promotion
- **L1 (Unit)**: `WaitlistEntry` positioning; duplicate queue entry rejection (`ALREADY_ON_WAITLIST`); queue size bounds (`WAITLIST_FULL`).
- **L2 (Contract)**: `JoinWaitlistRequest`, `LeaveWaitlistRequest`, `GetWaitlistStatusRequest`, `WaitlistJoinedOkReply`, `WaitlistPromotedEvent`.
- **L3 (Concurrency)**: Concurrent joins and leaves on a single sold-out show. FIFO ordering preserved without gaps or duplicate positions.
- **L4 (Auto-Promotion)**: When a held seat expires or a booked seat is cancelled, the head of the waitlist is automatically granted a temporary hold and notified via `WaitlistPromotedEvent`.
- **Pass Criteria**:
  - User 1 joins at pos 1, User 2 joins at pos 2. When User 1 leaves, User 2 shifts to pos 1.
  - Auto-promotion reserves the seat exclusively for the promoted user for the standard hold TTL.
  - If promoted user fails to confirm before TTL, seat cascades to pos 2.

---

### F4: Digital Ticket Generation & Verification
- **L1 (Unit)**: `Ticket` record creation; verification hash / QR payload formatting; tamper detection.
- **L2 (Contract)**: `GetTicketRequest` -> `TicketReply`; unknown ticket ID returns `TICKET_NOT_FOUND`.
- **Pass Criteria**:
  - Ticket issued upon successful `BOOK_SEAT` or `CONFIRM_HOLD`.
  - Ticket contains immutable booking ID, show ID, seat ID, user ID, price, and issued timestamp.

---

### F5: Bounded Booking History & Pagination
- **L1 (Unit)**: `PagedResult` offset/limit bounds slicing; validation of page sizes (`pageSize <= 50`, `pageSize >= 1`).
- **L2 (Contract)**: `GetBookingHistoryRequest` -> `BookingHistoryReply` with nested `BookingHistoryItem` list.
- **L4 (Stress/Security)**: Request with `pageSize = 1000` or `pageSize = 0` rejected with `INVALID_PAGE_SIZE`.
- **Pass Criteria**:
  - History returns only records owned by the authenticated session user.
  - Paging across total items produces disjoint, deterministic slices.
  - Offset beyond total items returns an empty list with `hasMore = false`.

---

### F6: Role-Based Access Control (RBAC) & Admin Governance
- **L1 (Unit)**: `UserRole.hasPermission(...)`; role elevation checks.
- **L2 (Contract)**: `AdminAddShowRequest`, `AdminUpdateSeatStatusRequest`, `AdminGetAuditLogsRequest`, `AdminCancelHoldRequest`.
- **L4 (Security/Authorization)**: Unauthenticated or non-admin (`USER` role) client sending admin requests receives `FORBIDDEN_ROLE` or `UNAUTHORIZED`.
- **Pass Criteria**:
  - Admin can forcibly cancel stuck holds, mark seats broken/maintenance (`MAINTENANCE`), and view audit logs.
  - Every administrative action emits an immutable `AuditLogEntry`.

---

### F7: High-Throughput Java NIO Server Refinements
- **L2 (Contract)**: Strict 16 KiB frame ceiling verification; UTF-8 LF-delimiter parser robustness.
- **L3 (Concurrency)**: 200 concurrent socket connections exchanging requests simultaneously without deadlocks on selector loop.
- **L4 (Fault/Fuzzing)**:
  - Transmission of 16,385 bytes without LF triggers immediate connection drop or `ERROR` reply (`FRAME_OVERSIZED`).
  - Partial writes simulated by splitting JSON lines across 1-byte TCP frames; server reassembles correctly.
  - Client abrupt RST packet does not crash selector thread or leak file descriptors.
- **Pass Criteria**:
  - Zero selector lockups (`selector.wakeup()` used correctly for outbound dispatch).
  - Outbound queue backpressure handled cleanly with `OP_WRITE` toggling.

---

### F8: UDP Local Area Network Discovery Beacon
- **L1 (Unit)**: `UdpBeaconPayload` serialization / deserialization to UDP packet datagram.
- **L2 (Contract)**: JSON structure matching `V2_PROTOCOL.md` specification.
- **L5 (E2E)**: Server beacon sender broadcasts every 5 seconds on port 8888; client UDP listener discovers active server IP, TCP port, TLS status, and available shows.
- **Pass Criteria**:
  - Beacon packet payload stays well below 512 bytes MTU boundary.
  - Swing client detects server without manual IP entry when running on the same LAN subnet.

---

### F9: Server Telemetry & Real-Time Metrics
- **L1 (Unit)**: Atomic counters for active connections, requests processed, booking rate, memory metrics.
- **L2 (Contract)**: `GetServerMetricsRequest` -> `ServerMetricsReply`; periodic `MetricsUpdatedEvent`.
- **Pass Criteria**:
  - Metrics snapshot matches live internal server state within ±1 sampling interval.
  - Monitored counters use lock-free atomic primitives (`LongAdder`, `AtomicInteger`).

---

### F10: TLS 1.3 Transport Security Mode
- **L1 (Unit)**: `TlsConfigMode` enum parsing and validation (`PLAINTEXT`, `TLS_OPTIONAL`, `TLS_REQUIRED`).
- **L5 (Security/E2E)**:
  - `SSLEngine` unwrapping and wrapping on NIO `SocketChannel`.
  - Self-signed dev certificate validation with client trust store.
  - Plaintext client connecting to `TLS_REQUIRED` port receives TLS handshake alert and is disconnected.
- **Pass Criteria**:
  - TLS 1.3 cipher suite negotiation succeeds.
  - Application layer contracts remain completely identical over plain TCP and TLS.

---

## 3. Automated Test Execution Suite

```bash
# 1. Run all unit and contract tests (L1, L2)
mvn -B test

# 2. Run specific V2 contract suite
mvn -B test -Dtest=V2ProtocolContractTest

# 3. Run full integration and concurrency verification
mvn -B verify
```

## 4. Pass / Fail Criteria Gates for CI/CD

1. **Zero Test Failures**: 100% of unit, contract, and concurrency tests must pass.
2. **Zero Compiler Warnings**: `-Werror` enforced on Java 21/17 compilation.
3. **Deterministic Execution**: No flaky tests permitted; all time-dependent tests must use `ClockService` test clock.
4. **Framing Compliance**: No message payload may exceed 16,384 bytes.
5. **No Regressions**: All v1 protocol tests (`ProtocolContractTest`, `NioBookingServerTest`, `ServerIntegrationTest`) must remain green.
