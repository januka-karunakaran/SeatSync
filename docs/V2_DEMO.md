# SeatSync 2.0: End-to-End Demonstration & Evaluation Script

This document provides exact, reproducible steps and CLI command lines for the SeatSync 2.0 10-step demonstration, networking proof, and evaluation defense.

---

## Prerequisites & Environment Setup

```bash
# 1. Build and verify all tests with zero compiler warnings
mvn clean test

# 2. Package application jar
mvn clean package -DskipTests
```

---

## 10-Step Demonstration Script

### Step 1: Launch NIO Server & UDP Discovery Probe
* **Concept Tested:** Java NIO non-blocking `SelectorLoop`, `ServerSocketChannel`, UDP `DatagramSocket`.
* **Action:**
  ```bash
  # Terminal 1: Launch NIO server on port 8080 (UDP discovery on 5051)
  java -cp "target/classes;target/dependency/*" com.seatsync.server.SeatSyncServer 8080

  # Terminal 2: Run UDP Discovery Probe
  java -cp "target/classes;target/dependency/*" com.seatsync.network.discovery.UdpDiscoveryProbe 5051
  ```
* **Network Proof:** Server responds with interface IP, configured TCP port, and TLS requirement flags without establishing a TCP connection.

---

### Step 2: Admin Login & Show Catalog Management
* **Concept Tested:** Role-Based Access Control (RBAC), Server-side permission authorization.
* **Action:**
  ```bash
  # Client 1 logs in as administrator
  {"type":"LOGIN","requestId":"req-admin-01","username":"admin","password":"adminPassword"}

  # Create a new show in the multi-show catalog
  {"type":"ADMIN_CREATE_SHOW","requestId":"req-admin-02","movieId":"MOV-01","title":"Interstellar Live","startTime":"2026-11-25T20:00:00Z","venue":"Main Auditorium"}
  ```
* **Network Proof:** Server returns `ADMIN_ACTION_OK`. If a customer account attempts `ADMIN_CREATE_SHOW`, server rejects with `FORBIDDEN_ROLE`.

---

### Step 3: Simultaneous Show Viewing & Atomic Seat Holding
* **Concept Tested:** Asynchronous server-push event broadcast (`SEAT_HELD`), selective subscriptions.
* **Action:**
  * Client A and Client B both subscribe to `SHOW-01`:
    ```json
    {"type":"SUBSCRIBE_EVENTS","requestId":"sub-01","showId":"SHOW-01"}
    ```
  * Client A places a temporary hold on seat `B3`:
    ```json
    {"type":"HOLD_SEAT","requestId":"hold-01","showId":"SHOW-01","seatId":"B3","holdDurationSeconds":120}
    ```
* **Network Proof:** Both Client A and Client B immediately receive asynchronous `SEAT_HELD_EVENT` for `B3` without polling or clicking Refresh.

---

### Step 4: Competing Seat Reservation Conflict Detection
* **Concept Tested:** Mutex locking and atomic state transition under simultaneous contention.
* **Action:**
  * While `B3` is held by Client A, Client B attempts to hold or book `B3`:
    ```json
    {"type":"HOLD_SEAT","requestId":"hold-02","showId":"SHOW-01","seatId":"B3"}
    ```
* **Network Proof:** Server returns `HOLD_REJECTED` with error `SEAT_ALREADY_HELD`. Exactly one client wins the hold.

---

### Step 5: Server-Side Hold Expiry & Automatic Release
* **Concept Tested:** Scheduled server-side TTL expiry without client-side coordination.
* **Action:** Client A allows the 120-second hold timer to lapse without confirmation.
* **Network Proof:** Server-side timer triggers release, emitting `SEAT_RELEASED_EVENT` to all subscribers. Seat `B3` returns to `AVAILABLE`.

---

### Step 6: Confirmation, Digital Ticket Issuance & History View
* **Concept Tested:** SHA-256 cryptographic ticket token, bounded paginated history queries.
* **Action:**
  * Client A confirms an alternate seat:
    ```json
    {"type":"CONFIRM_HOLD","requestId":"conf-01","holdId":"HLD-101","idempotencyKey":"key-abc"}
    ```
  * Query issued ticket:
    ```json
    {"type":"GET_TICKET","requestId":"tck-01","ticketId":"TCK-XXXX"}
    ```
  * Query booking history:
    ```json
    {"type":"GET_BOOKING_HISTORY","requestId":"hist-01","pageNumber":0,"pageSize":10}
    ```
* **Network Proof:** Server returns `TICKET_REPLY` with tamper-proof verification hash. Intruding users cannot view another user's ticket.

---

### Step 7: Waitlist FIFO Promotion on Cancellation
* **Concept Tested:** Concurrent FIFO queue and private user notification.
* **Action:**
  * Client C joins waitlist for sold-out show:
    ```json
    {"type":"JOIN_WAITLIST","requestId":"wl-01","showId":"SHOW-01"}
    ```
  * Client A cancels their booking:
    ```json
    {"type":"CANCEL_BOOKING","requestId":"can-01","bookingId":"BKG-01"}
    ```
* **Network Proof:** Client A's digital ticket is invalidated. Server emits `WAITLIST_PROMOTED_EVENT` privately to Client C with a 60-second priority offer.

---

### Step 8: Network Disconnect Recovery, Idempotent Retries & Resync
* **Concept Tested:** Client reconnect coordinator, idempotent deduplication, event version gap detection.
* **Action:**
  * Client loses connection immediately after transmitting `BOOK_SEAT`.
  * Client reconnects and retries identical request with the **same `requestId`**:
    ```json
    {"type":"BOOK_SEAT","requestId":"req-retry-99","showId":"SHOW-01","seatId":"A1"}
    ```
* **Network Proof:**
  * `IdempotentRequestDispatcher` detects the retransmission, serving the original `BOOK_OK` without double-booking or throwing errors.
  * If the client missed events during disconnection, `ReconnectCoordinator` detects `version > lastKnown + 1` and issues an authoritative `GET_SEATS` snapshot resync.

---

### Step 9: 50-Client Concurrency Contention & Metrics
* **Concept Tested:** High-concurrency NIO thread pool, p95 latency measurement, zero double-booking proof.
* **Action:**
  ```bash
  # Execute automated virtual client load harness
  mvn test -Dtest=VirtualClientLoadHarnessTest
  ```
* **Network Proof:** 50 virtual clients contend for 10 seats simultaneously over real TCP sockets. Exactly 10 clients succeed, 40 are cleanly rejected, zero errors occur, and p95 latency remains low.

---

### Step 10: TLS 1.3 Transport & Untrusted Certificate Rejection
* **Concept Tested:** Java 21 `SSLEngine` non-blocking encryption, mutual trust validation.
* **Action:**
  * Connect using trusted truststore containing valid localhost CA:
    ```bash
    # Handshake completes; communications encrypted over wire
    ```
  * Connect using an untrusted or forged certificate:
    ```bash
    # Handshake fails immediately; connection terminated; no plaintext fallback
    ```
* **Network Proof:** TLS 1.3 encrypted channel protects credentials and payloads against wire eavesdropping.
