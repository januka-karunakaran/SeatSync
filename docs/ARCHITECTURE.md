# SeatSync Architecture Specification

## 1. Executive Summary

SeatSync is an undergraduate real-time distributed seat reservation system designed for high concurrency, low latency, and deterministic state consistency. The system operates on a single show (`SHOW-01`) comprising an auditorium grid of 40 seats (`A1`–`E8`). Multiple concurrent desktop graphical clients connect over persistent TCP channels to reserve seats, cancel existing reservations, and receive instantaneous server-push broadcasts whenever any seat status changes.

---

## 2. System Architecture Diagram

```
+-----------------------------------------------------------------------------------+
|                            SEATSYNC SYSTEM ARCHITECTURE                           |
+-----------------------------------------------------------------------------------+

   +-----------------------+                    +-----------------------+
   |   Swing Client #1     |                    |   Swing Client #N     |
   | (java.net.Socket TCP) |                    | (java.net.Socket TCP) |
   +-----------+-----------+                    +-----------+-----------+
               |                                            |
               | Persistent TCP Connection (UTF-8 JSON)     |
               +--------------------+  +--------------------+
                                    |  |
                                    v  v
+-----------------------------------------------------------------------------------+
|                               SEATSYNC SERVER                                     |
|                                                                                   |
|  +-----------------------------------------------------------------------------+  |
|  | NETWORK TRANSPORT LAYER (Member 01)                                         |  |
|  | - ServerSocketChannel (Non-blocking, port 8080)                             |  |
|  | - Selector Loop (OP_ACCEPT, OP_READ, OP_WRITE)                              |  |
|  | - ByteBuffer Frame Accumulator (Delimiter: \n, Max Frame: 16 KiB)          |  |
|  | - Channel Registry & Connection Map (SocketChannel -> RequestContext)      |  |
|  +---------------------------------------+-------------------------------------+  |
|                                          |                                        |
|                                          | Dispatches complete frames             |
|                                          v                                        |
|  +-----------------------------------------------------------------------------+  |
|  | DISPATCHER & SESSION LAYER (Member 02 / Member 04)                          |  |
|  | - RequestDispatcher (Validates JSON syntax, discriminates MessageType)     |  |
|  | - RequestContext (Minimal session: sessionId, authenticatedUserId, send())   |  |
|  | - Input Validator (Frame limit enforcement, seat ID format check)          |  |
|  +--------------------+----------------------------------+---------------------+  |
|                       |                                  |                        |
|    Auth requests      |                                  | Booking requests       |
|    (LOGIN, LOGOUT)    |                                  | (GET_SEATS, BOOK,      |
|                       v                                  |  CANCEL_BOOKING)       |
|  +--------------------+---------------+                  v                        |
|  | SECURITY & AUTH SERVICE (Member 04)|  +---------------+---------------------+  |
|  | - AuthService.login(...)           |  | WORKER THREAD POOL (Member 03)      |  |
|  | - In-memory user store             |  | - ExecutorService (Fixed/Cached)    |  |
|  | - Session authorization token      |  | - Decouples NIO Selector from logic |  |
|  +--------------------+---------------+  +---------------+---------------------+  |
|                       |                                  |                        |
|                       | Identity bound                   | Delegates to           |
|                       v                                  v                        |
|  +-----------------------------------------------------------------------------+  |
|  | BUSINESS & CONCURRENCY ENGINE (Member 03)                                   |  |
|  | - ReservationService (book, cancel, snapshot)                              |  |
|  | - LockManager (Seat-level ReentrantLock / ConcurrentHashMap)                |  |
|  | - Atomic Show Version counter (Monotonic increment per transaction)         |  |
|  | - In-Memory Seat Store: SHOW-01 (40 Seats: A1-E8)                           |  |
|  +---------------------------------------+-------------------------------------+  |
|                                          |                                        |
|                                          | Emits seat mutation event              |
|                                          v                                        |
|  +-----------------------------------------------------------------------------+  |
|  | ASYNCHRONOUS BROADCAST MANAGER (Member 01 / Member 02)                      |  |
|  | - SeatChangeListener.onSeatUpdated(showId, version, seats)                 |  |
|  | - Iterates active RequestContext instances                                  |  |
|  | - Invokes thread-safe RequestContext.send(SEAT_UPDATED)                     |  |
|  +-----------------------------------------------------------------------------+  |
+-----------------------------------------------------------------------------------+
```

---

## 3. Layer Breakdown and Responsibilities

### 3.1 Network Transport Layer (Java NIO)
- **Role:** Non-blocking connection management and frame accumulation.
- **Components:** `ServerSocketChannel`, `Selector`, `SelectionKey`, `ByteBuffer`.
- **Operating Model:** A dedicated single-threaded selector loop monitors I/O events. When bytes arrive on a client `SocketChannel`, the network layer appends incoming bytes to a session read buffer until a newline delimiter (`\n`) is encountered.
- **Safety Boundary:** Imposes a strict 16 KiB limit per frame. If a client transmits more than 16,384 bytes without a newline delimiter, the channel is immediately severed to defend against buffer exhaustion.

### 3.2 Dispatching and Session Abstraction Layer
- **Role:** Protocol discrimination, JSON unmarshalling, and session context binding.
- **Components:** `RequestDispatcher`, `RequestContext`, `ProtocolJson`.
- **Identity Invariant:** Client requests **never** provide their own user identity for transactional operations (`BOOK_SEAT`, `CANCEL_BOOKING`). The requesting identity is fetched exclusively from the server-side `RequestContext.getAuthenticatedUserId()`.

### 3.3 Security & Authentication Layer
- **Role:** Identity verification and access authorization.
- **Components:** `AuthService`, `AuthResult`, credential hashing utilities.
- **Planned Security:** Explicit authentication via username/password and strict authorization on transactional actions. **Note:** In alignment with assignment constraints, no TLS/SSL encryption is claimed or implemented. Wire traffic consists of UTF-8 JSON over standard TCP sockets.

### 3.4 Business & Concurrency Engine
- **Role:** Atomic seat reservation, cancellation, and snapshot generation.
- **Components:** `ReservationService`, `BookingResult`, `CancelResult`, `SeatSnapshot`.
- **Show Model:** Single pre-configured auditorium (`SHOW-01`) containing 40 seats (`A1` through `E8`).
- **Concurrency Strategy:** Offloaded to a dedicated `ExecutorService` worker thread pool to prevent blocking the NIO Selector. Fine-grained locks (or seat-level locks) prevent race conditions when two clients attempt to book the same seat simultaneously.

### 3.5 Asynchronous Broadcast Subsystem
- **Role:** Instantaneous real-time update propagation.
- **Components:** `SeatChangeListener`, `SeatUpdatedEvent`.
- **Workflow:** Whenever a booking or cancellation mutates seat availability, the booking engine notifies the `SeatChangeListener`. The listener serializes a `SEAT_UPDATED` event and invokes `RequestContext.send(...)` across all open client connections. `RequestContext.send(...)` is thread-safe.

---

## 4. In-Memory Data Model (SHOW-01)

The system does not utilize an external relational or NoSQL database. All state resides in heap memory:

```
Rows: A, B, C, D, E (5 rows)
Cols: 1, 2, 3, 4, 5, 6, 7, 8 (8 seats per row)
Total Seats: 40 seats (A1 to E8)

+----+----+----+----+----+----+----+----+
| A1 | A2 | A3 | A4 | A5 | A6 | A7 | A8 |
+----+----+----+----+----+----+----+----+
| B1 | B2 | B3 | B4 | B5 | B6 | B7 | B8 |
+----+----+----+----+----+----+----+----+
| C1 | C2 | C3 | C4 | C5 | C6 | C7 | C8 |
+----+----+----+----+----+----+----+----+
| D1 | D2 | D3 | D4 | D5 | D6 | D7 | D8 |
+----+----+----+----+----+----+----+----+
| E1 | E2 | E3 | E4 | E5 | E6 | E7 | E8 |
+----+----+----+----+----+----+----+----+
```

Every seat is represented by a `SeatView` record (`seatId`, `status`, `bookedBy`). A monotonically increasing 64-bit version counter tracks state revisions.
