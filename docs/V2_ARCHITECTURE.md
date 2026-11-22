# SeatSync 2.0 System Architecture Specification

## 1. High-Level Architecture Overview

SeatSync 2.0 expands the core Java NIO selector architecture into a multi-service event-driven engine. The design cleanly decouples network I/O from business processing, maintains non-blocking transport loops, and introduces dedicated background services for hold TTL expiration, FIFO waitlist promotion, and UDP discovery beacons.

```
+---------------------------------------------------------------------------------------------------------+
|                                    SEATSYNC 2.0 SYSTEM ARCHITECTURE                                     |
+---------------------------------------------------------------------------------------------------------+

           +-------------------------+                       +-------------------------+
           |     Desktop Client 1    |                       |     Desktop Client N    |
           |   (Swing + TcpClient)   |                       |   (Swing + TcpClient)   |
           +------------+------------+                       +------------+------------+
                        |                                                 |
                        | Plain TCP (8080) / Optional TLS (8443)          | UDP Discovery (8888)
                        v                                                 v
+---------------------------------------------------------------------------------------------------------+
| SEATSYNC 2.0 SERVER                                                                                     |
|                                                                                                         |
|  +---------------------------------------------------------------------------------------------------+  |
|  | TRANSPORT LAYER (Member 02)                                                                       |  |
|  | - ServerSocketChannel (Non-blocking, Port 8080)                                                   |  |
|  | - Optional SSLEngine / Secure Socket Channel (Port 8443)                                          |  |
|  | - Single-Threaded Selector Loop (OP_ACCEPT, OP_READ, OP_WRITE with wakeup queue)                   |  |
|  | - UDP Discovery Beacon Broadcaster (DatagramChannel, Port 8888, 5s interval)                     |  |
|  | - Per-Session Input Accumulator (16 KiB ceiling, LF delimiter, UTF-8 character decoding)            |  |
|  +-------------------------------------------------+-------------------------------------------------+  |
|                                                    |                                                    |
|                                                    | Complete JSON Lines                                |
|                                                    v                                                    |
|  +---------------------------------------------------------------------------------------------------+  |
|  | DISPATCHER & GOVERNANCE LAYER (Member 04 / Member 02)                                             |  |
|  | - SecurityRequestDispatcher (Authentication, Session Binding, Input Validation)                   |  |
|  | - RBAC Guard: Role Verification (CUSTOMER, OPERATOR, ADMIN)                                        |  |
|  | - Audit Logger: Writes sensitive administrative actions to AuditLogEntry queue                    |  |
|  | - Telemetry Interceptor: Records request duration & outcome to MetricsService                     |  |
|  +-------------------------------------------------+-------------------------------------------------+  |
|                                                    |                                                    |
|                                                    | Authorized Requests                                |
|                                                    v                                                    |
|  +---------------------------------------------------------------------------------------------------+  |
|  | WORKER CONCURRENCY POOL (Member 03)                                                               |  |
|  | - WorkerPoolManager: Bounded Worker ThreadPool (Fixed thread count)                               |  |
|  | - Decouples Selector I/O thread from stateful business operations                                 |  |
|  +-------------------+-----------------------------+-----------------------------+-------------------+  |
|                      |                             |                             |                      |
|                      v                             v                             v                      |
|  +-----------------------+   +-------------------------+   +-------------------------+   +------------+ |
|  | CATALOG SERVICE (M3)  |   | HOLD & BOOKING (M3)     |   | FIFO WAITLIST (M3)      |   | TICKETS/   | |
|  | - Multi-Show Catalog  |   | - Two-Phase Hold Engine |   | - Queue per Show/Tier   |   | HISTORY(M1)| |
|  | - Layouts & SeatTiers |   | - TTL Clock Expiration  |   | - Auto-promotion on     |   | - Checksum | |
|  | - Available counts    |   | - Idempotent Confirm    |   |   hold release / cancel |   | - Paged    | |
|  +-----------------------+   +-------------------------+   +-------------------------+   +------------+ |
|                      |                             |                             |                      |
|                      +-----------------------------+-----------------------------+                      |
|                                                    | State Changes                                      |
|                                                    v                                                    |
|  +---------------------------------------------------------------------------------------------------+  |
|  | ASYNCHRONOUS EVENT PUB/SUB & BROADCASTER (Member 02 / Member 01)                                  |  |
|  | - EventSubscriptionService: Tracks active session subscriptions per showId                        |  |
|  | - Filtered Broadcast: Sends SEAT_HELD, SEAT_RELEASED, SEAT_UPDATED only to interested clients    |  |
|  | - Thread-safe RequestContext.send() outbound queues                                               |  |
|  +---------------------------------------------------------------------------------------------------+  |
+---------------------------------------------------------------------------------------------------------+
```

---

## 2. Component Subsystems Breakdown

### 2.1 Transport & Discovery Subsystem (Member 02)
- **NIO Plain TCP Transport:** Port `8080` (or `5050`) multiplexed by `NioBookingServer`. Dedicated selector thread processes channel readiness.
- **TLS Secure Transport:** Port `8443` supporting TLS 1.3/1.2 via non-blocking `SSLEngine` wraps. `TlsConfigMode` controls whether TLS is disabled, optional (co-existing with plain TCP), or enforced.
- **UDP Discovery Beacon:** Runs on a dedicated daemon thread broadcasting `UdpBeaconPayload` JSON packets to `255.255.255.255:8888` every 5 seconds. Clients passively listen on UDP 8888 to automatically locate the active server without manual IP configuration.

### 2.2 Concurrency & Worker Execution Subsystem (Member 03)
- **Bounded Worker Pool:** The selector thread never performs business logic, locking, or ticket generation. All parsed client frames are handed off to `WorkerPoolManager` backed by a bounded `ArrayBlockingQueue`.
- **Overload Protection:** If the worker queue reaches maximum capacity (e.g. 10,000 requests), requests are rejected with `ErrorCode.RATE_LIMITED` rather than exhausting server heap.

### 2.3 Two-Phase Seat Hold & TTL Subsystem (Member 03)
- **State Progression:** `AVAILABLE` -> `HELD` -> `BOOKED`.
- **Hold Duration (TTL):** Standard hold duration is 120 seconds.
- **TTL Reaper:** A background scheduled executor periodically sweeps active holds. Any hold where `currentTimeMillis >= expirationTimestamp` is released back to `AVAILABLE`, emitting a `SEAT_RELEASED_EVENT`.
- **Pluggable Clock:** All time checks depend on `ClockService`, allowing test suites to deterministically simulate time jumps without `Thread.sleep()`.
- **Idempotency Guarantee:** `ConfirmHoldRequest` requires a client-generated `idempotencyKey`. If a client retries confirmation due to network timeout, the server returns the cached `BookingResult` without double-booking or creating redundant ticket entries.

### 2.4 FIFO Waitlist Subsystem (Member 03)
- **Queue Semantics:** Strict First-In, First-Out order per show.
- **Auto-Promotion Engine:** When a held seat expires or a booking is cancelled:
  1. The seat engine checks if the show's waitlist is non-empty.
  2. If enqueued users exist, the head user is dequeued and awarded an exclusive hold on the seat.
  3. The server emits a `WAITLIST_PROMOTED_EVENT` targeted to the promoted user's session and broadcasts updated show availability.

### 2.5 Security, RBAC & Audit Subsystem (Member 04)
- **User Roles:** `CUSTOMER` (standard booking/waitlist), `OPERATOR` (viewing reports/metrics), `ADMIN` (full show layout editing, audit logs, emergency hold revocation).
- **Session Identity:** Authentication binds the user identity and role to `RequestContext`. Client payloads are forbidden from passing `userId`.
- **Audit Logging:** All administrative overrides (`ADMIN_ADD_SHOW`, `ADMIN_UPDATE_SEAT_STATUS`, `ADMIN_CANCEL_HOLD`) produce immutable `AuditLogEntry` records capturing timestamp, admin ID, action, and target resource.

### 2.6 Digital Tickets & Paged History (Member 01)
- **Ticket Verification:** Issued tickets contain an immutable SHA-256 verification code computed over `ticketId + bookingId + showId + seatId + userId + priceInCents`.
- **Bounded Pagination:** `HistoryService` enforces `PagedResult.MAX_PAGE_SIZE = 50`. Requests asking for oversized pages are rejected with `ErrorCode.INVALID_PAGE_SIZE`.

### 2.7 Pub/Sub Subscription Subsystem (Member 02)
- **Channel Partitioning:** Clients subscribe to events per `showId`.
- **Selective Event Routing:** When seat `B4` in `SHOW-01` changes status, the server routes `SEAT_HELD_EVENT` or `SEAT_UPDATED` only to sessions actively viewing `SHOW-01`, reducing network overhead by up to 90% in multi-show deployments.
