# SeatSync - Developer Setup & Team Onboarding Guide

> **Target Audience:** SeatSync Team Members (Members 01 through 05)  
> **Sprint Deadline:** 26 November 2026  
> **Status:** Member 01 (TCP Desktop Client) & Member 02 (Java NIO Server) Complete  

---

## 1. Prerequisites & Environment Setup

Before starting work, ensure your development workstation meets the minimum environment specifications:

| Requirement | Supported Versions | Verification Command |
| :--- | :--- | :--- |
| **Java Development Kit (JDK)** | Java 17 LTS or Java 21 LTS | `java -version` and `javac -version` |
| **Apache Maven** | Maven 3.8+ or 3.9+ | `mvn -version` |
| **Git** | 2.30+ | `git --version` |
| **Recommended IDE** | IntelliJ IDEA, VS Code, or Eclipse | Ensure JDK 17/21 is set as Project SDK |

> [!IMPORTANT]
> The project compiles with `-Xlint:all` and `-Werror` enabled in `pom.xml`. This means **zero compiler warnings are allowed**; any warning will fail the Maven build. Keep imports clean and avoid raw types or unclosed resources.

---

## 2. Cloning & Initial Verification

### 2.1 Clone the Repository
```powershell
git clone https://github.com/januka-karunakaran/SeatSync.git
cd SeatSync
```

### 2.2 Verify Build and Tests
Run the complete automated test suite (all unit tests, integration tests, and protocol contracts):
```powershell
mvn clean test
```
*Expected Output:* `BUILD SUCCESS`, `Tests run: 20, Failures: 0, Errors: 0, Skipped: 0`.

### 2.3 Package the Application
Compile all classes and package dependencies into `target/dependency/`:
```powershell
mvn clean package
```

---

## 3. How to Run the Project Locally

SeatSync is a client-server desktop system. You need to run **one server** and **one or more client windows**.

### 3.1 Start the Central Java NIO Server
Open a terminal in the project root:
```powershell
# Local demo only; never use this password for real accounts.
$env:SEATSYNC_DEMO_PASSWORD = 'password123'

# Method A: Windows Batch Script (Fastest)
.\scripts\start-server.bat

# Method B: Maven Exec
mvn exec:java -Dexec.mainClass="com.seatsync.server.SeatSyncServer"
```
*Expected Server Output:*
```text
=================================================================
  SeatSync Non-Blocking Java NIO Server (Java 21)
  Author: Member 02 (Network & Cross-Module Server Integration)
=================================================================
[INFO] Host: 127.0.0.1
[INFO] Port: 8080
[INFO] Max Frame Size: 16384 bytes (16 KiB)
[INFO] Transport: Non-blocking ServerSocketChannel + Selector loop
[INFO] SeatSync NIO Server successfully listening on 127.0.0.1:8080
```

### 3.2 Start Desktop Clients (Swing GUI)
Leave the server running. Open a **new terminal tab or window**:
```powershell
# Method A: Windows Batch Script
.\scripts\start-client.bat

# Method B: Maven Exec
mvn exec:java -Dexec.mainClass="com.seatsync.client.SeatSyncClient"
```
*(The graphical Swing Login window will appear on your desktop.)*

After login, use the left navigation to open Home, Movies, Shows, Seat Map, My
Tickets, Waitlist, or Admin. The existing seat workflow remains under Seat Map;
the v2 client also supports holds, waitlisting, ticket history, and admin
management/monitoring. Admin controls require an ADMIN account. See
[desktop client navigation](CLIENT_UI.md).

The five demo usernames are `alice`, `bob`, `carol`, `dave`, and `erin`; use the
locally configured demo password. The default local server is plaintext on
loopback only. To use TLS, configure a trusted client PKCS12 truststore and
select **Require TLS 1.3** in the login window; see
[TLS and server discovery](CLIENT_TLS_DISCOVERY.md) and
[security limitations](SECURITY_LIMITATIONS.md).

---

## 4. Testing Real-Time Multi-Client Synchronization

To test multi-client communication and verify that live broadcasts work:

1. **Launch Server:** Run `.\scripts\start-server.bat`.
2. **Launch Client 1:** In a second terminal, run `.\scripts\start-client.bat`.
   - Log in with Username: `alice`, Password: `password123`.
   - The interactive 40-seat cinema grid (A1 to E8) will open.
3. **Launch Client 2:** In a third terminal, run `.\scripts\start-client.bat`.
   - Log in with Username: `bob`, Password: `password123`.
4. **Demonstrate Simultaneous Booking:**
   - In Client 1, click seat **C4** and click **Book Seat**.
   - Client 1 shows **C4** as **Blue (Mine)** with booking confirmation.
   - Client 2 instantly updates **C4** to **Red (Booked)** in real time without refreshing!
5. **Demonstrate Cancellation:**
   - In Client 1, select **C4** and click **Cancel Booking**.
   - Both clients instantly see **C4** return to **Green (Open)**.

---

## 5. Workload Allocation & Guide for Next Members

Each team member owns a distinct networking concept and set of Java classes:

```
                  ┌─────────────────────────────────────────┐
                  │      Member 01: Desktop Swing Client    │
                  │ (TcpClient, SeatGridPanel, LoginFrame)  │
                  └────────────────────┬────────────────────┘
                                       │ TCP Socket (Port 8080)
                                       ▼
                  ┌─────────────────────────────────────────┐
                  │     Member 02: Central Java NIO Server  │
                  │   (NioBookingServer, ClientSession)     │
                  └────────────────────┬────────────────────┘
                                       │ RequestContext / Frames
                                       ▼
                  ┌─────────────────────────────────────────┐
                  │    Member 03: Multithreaded Dispatcher  │
                  │ (RequestDispatcher, WorkerPoolManager)  │
                  └──────────┬───────────────────┬──────────┘
                             │                   │
                             ▼                   ▼
     ┌───────────────────────────────┐   ┌───────────────────────────────┐
     │  Member 04: Synchronization   │   │  Member 05: Security & Auth   │
     │     (ReservationService,      │   │    (AuthService, PBKDF2,      │
     │      SeatInventory, Lock)     │   │      Session Security)        │
     └───────────────────────────────┘   └───────────────────────────────┘
```

### 5.1 Member 01 (COMPLETED)
- **Concept:** TCP Client-Server Communication (`java.net.Socket`, streams, background reader thread, Swing GUI).
- **Branch:** `feature/member01-tcp-client`
- **Owned Classes:**
  - `com.seatsync.client.SeatSyncClient`
  - `com.seatsync.client.TcpClient`
  - `com.seatsync.client.ClientMessageHandler`
  - `com.seatsync.ui.LoginFrame`
  - `com.seatsync.ui.BookingFrame`
  - `com.seatsync.ui.SeatGridPanel`
  - `com.seatsync.ui.ThemedButton`

### 5.2 Member 02 (COMPLETED)
- **Concept:** Java NIO Non-Blocking I/O (`ServerSocketChannel`, `SocketChannel`, `Selector`, `ByteBuffer`).
- **Branch:** `feature/01-nio-server`
- **Owned Classes:**
  - `com.seatsync.network.NioBookingServer`
  - `com.seatsync.network.ClientSession`
  - `com.seatsync.network.ClientSessionRegistry`
  - `com.seatsync.network.ServerConfig`

---

### 5.3 Member 03 (NEXT: Multithreading & Request Dispatching)
- **Primary Concept:** Server-Side Multithreading & Thread Pools
- **Feature Branch:** `feature/member03-multithreading`
- **Owned Classes / Files:**
  - `com.seatsync.concurrency.WorkerPoolManager.java`
  - `com.seatsync.concurrency.RequestTask.java`
  - `com.seatsync.server.BookingRequestProcessor.java`
  - Replace `com.seatsync.server.MockRequestDispatcher.java` with production `RequestDispatcherImpl`
- **Implementation Instructions:**
  1. Create a branch:
     ```powershell
     git checkout main
     git pull origin main
     git checkout -b feature/member03-multithreading
     ```
  2. Implement `WorkerPoolManager` managing a fixed `ExecutorService` (e.g. 8–16 worker threads).
  3. Ensure that when `ClientSession.handleRead` parses a JSON line, it submits the work to the thread pool rather than executing application logic directly on the NIO Selector thread.
  4. Ensure results are sent back via `context.send(...)` (which safely enqueues outbound frames back to NIO).
  5. Include thread name in logs: `Thread.currentThread().getName()` to demonstrate concurrent execution during grading.

---

### 5.4 Member 04 (NEXT: Synchronization & Seat State)
- **Primary Concept:** Shared-State Concurrency & Double-Booking Prevention
- **Feature Branch:** `feature/member04-seat-synchronization`
- **Owned Classes / Files:**
  - `com.seatsync.booking.Seat.java`
  - `com.seatsync.booking.Reservation.java`
  - `com.seatsync.booking.SeatInventory.java`
  - `com.seatsync.booking.ReservationServiceImpl.java` (implements `com.seatsync.common.contracts.ReservationService`)
- **Implementation Instructions:**
  1. Create a branch:
     ```powershell
     git checkout main
     git pull origin main
     git checkout -b feature/member04-seat-synchronization
     ```
  2. Implement atomic check-and-reserve for all 40 seats (`A1`–`E8`).
  3. Use fine-grained `ReentrantLock` or `synchronized` block around seat reservation changes so that simultaneous requests for the same seat result in **exactly one winner** (`BOOK_OK`) and one rejection (`BOOK_REJECTED` with `ErrorCode.SEAT_ALREADY_BOOKED`).
  4. Write a JUnit 5 multi-threaded race condition test (e.g. 10 concurrent threads competing for seat `C4`) proving thread safety.

---

### 5.5 Member 05 (NEXT: Authentication & Security)
- **Primary Concept:** Secure Network Programming & Access Control
- **Feature Branch:** `feature/member05-auth-security`
- **Owned Classes / Files:**
  - `com.seatsync.security.AuthServiceImpl.java` (implements `com.seatsync.common.contracts.AuthService`)
  - `com.seatsync.security.PasswordHasher.java`
  - `com.seatsync.security.User.java`
  - `com.seatsync.security.UserRepository.java`
  - `com.seatsync.security.AuthorizationService.java`
- **Implementation Instructions:**
  1. Create a branch:
     ```powershell
     git checkout main
     git pull origin main
     git checkout -b feature/member05-auth-security
     ```
  2. Implement PBKDF2 with unique salts for user password hashing; do not store plain-text passwords.
  3. Create pre-configured demo accounts (`alice`, `bob`, `charlie`, `admin`).
  4. Enforce session checking: reject `BOOK_SEAT` or `CANCEL_BOOKING` if session is unauthenticated (`AUTH_REQUIRED`).
  5. Enforce booking ownership: verify that only the user who booked a seat can cancel it; unauthorized attempts return `NOT_BOOKING_OWNER`.

---

## 6. Shared Contracts Reference

All requests and replies must conform to the agreed protocol records in `com.seatsync.common.protocol.*`:

| Message Type | Direction | Payload Class |
| :--- | :--- | :--- |
| `LOGIN` | Client -> Server | `LoginRequest(requestId, username, password)` |
| `LOGIN_OK` | Server -> Client | `LoginOkReply(requestId, userId, username)` |
| `LOGIN_FAILED` | Server -> Client | `LoginFailedReply(requestId, errorCode, message)` |
| `GET_SEATS` | Client -> Server | `GetSeatsRequest(requestId, showId)` |
| `SEAT_SNAPSHOT` | Server -> Client | `SeatSnapshotReply(requestId, showId, version, seats)` |
| `BOOK_SEAT` | Client -> Server | `BookSeatRequest(requestId, showId, seatId)` |
| `BOOK_OK` | Server -> Client | `BookOkReply(requestId, bookingId, showId, seatId, message)` |
| `BOOK_REJECTED` | Server -> Client | `BookRejectedReply(requestId, errorCode, message)` |
| `CANCEL_BOOKING`| Client -> Server | `CancelBookingRequest(requestId, bookingId)` |
| `CANCEL_OK` | Server -> Client | `CancelOkReply(requestId, bookingId, seatId, message)` |
| `CANCEL_REJECTED`| Server -> Client | `CancelRejectedReply(requestId, errorCode, message)` |
| `SEAT_UPDATED` | Server -> Broadcast | `SeatUpdatedEvent(showId, version, List<SeatView> seats)` |

---

## 7. Troubleshooting & Common Pitfalls

### Issue 1: `java.net.BindException: Address already in use: bind`
- **Cause:** A previously started server is still running on port `8080`.
- **Fix:** In PowerShell, find and kill the process holding port 8080:
  ```powershell
  # Find PID
  Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue | Select-Object OwningProcess

  # Stop process
  Stop-Process -Id <PID> -Force
  ```

### Issue 2: Compilation failure with `-Werror`
- **Cause:** Maven compiler treats all warnings as errors.
- **Fix:** Fix any unused imports, unclosed resources, or raw types before committing.

### Issue 3: Swing UI buttons look white-on-white on Windows
- **Cause:** Windows native button renderer overrides custom background colors unless handled explicitly.
- **Fix:** Always use `ThemedButton` or call `btn.setUI(new javax.swing.plaf.basic.BasicButtonUI())` with `btn.setOpaque(true)`.

### Issue 4: Slow or freezing client UI
- **Cause:** Performing blocking network I/O on the Swing Event Dispatch Thread (EDT).
- **Fix:** Run all network calls (`connect`, `sendRequest`) on background worker threads, and update Swing models/views strictly using `SwingUtilities.invokeLater(...)`.
