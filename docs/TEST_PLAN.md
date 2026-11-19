# SeatSync Verification & Test Plan

## 1. Overview & Quality Objectives

The SeatSync verification strategy guarantees deterministic state consistency, zero race conditions in seat reservations, strict conformance to wire protocol contracts, and resilience under concurrent client load.

---

## 2. Testing Levels & Matrix

### 2.1 Level 1: Protocol & Contract Unit Tests (Delivered in PR00)
- **Target:** `com.seatsync.common.ProtocolContractTest`
- **Scope:**
  - JSON serialization and deserialization of all 5 request types (`LOGIN`, `GET_SEATS`, `BOOK_SEAT`, `CANCEL_BOOKING`, `LOGOUT`).
  - JSON serialization and deserialization of all 9 reply types (`LOGIN_OK`, `LOGIN_FAILED`, `SEAT_SNAPSHOT`, `BOOK_OK`, `BOOK_REJECTED`, `CANCEL_OK`, `CANCEL_REJECTED`, `LOGOUT_OK`, `ERROR`).
  - Real-time server push event (`SEAT_UPDATED`) serialization and validation that events omit client `requestId`.
  - Wire framing limit enforcement: verifying payloads <= 16 KiB pass and payloads > 16 KiB throw `IllegalArgumentException`.
  - Verification that untrusted client actors (`userId`) are excluded from `BOOK_SEAT` and `CANCEL_BOOKING` request payloads.
  - Immutability of domain records (`SeatView`, `SeatSnapshot`, `BookingResult`, `CancelResult`, `AuthResult`) and validation of auditorium seat IDs (`A1`–`E8`).
  - Session contract verification (`RequestContext`).

### 2.2 Level 2: Component Unit Tests (M1–M5 Milestones)
- **Authentication Tests (`AuthServiceTest` - Member 04):** Validates password verification, nonexistent users, rate limiting, and password memory hygiene.
- **Booking Engine Tests (`ReservationServiceTest` - Member 03):** Validates booking available seats, attempting to book already-booked seats, cancelling owned bookings, and rejecting cancellation of non-owned bookings.
- **NIO Buffer & Framing Tests (`NioFrameAccumulatorTest` - Member 01):** Validates partial reads, split frames across chunks, multiple frames in a single buffer, and disconnection on 16 KiB frame overflow.
- **Swing Client Model Tests (`SeatGridModelTest` - Member 05):** Verifies state transitions in GUI models upon arrival of `SEAT_SNAPSHOT` and `SEAT_UPDATED` events.

### 2.3 Level 3: Concurrency Stress Testing
- **Multi-threaded Contention Test:** Launch 50 concurrent worker threads attempting to book the identical seat (`C4`) simultaneously.
- **Acceptance Criterion:** Exactly **1** thread receives `BOOK_OK`; exactly **49** threads receive `BOOK_REJECTED` (`SEAT_ALREADY_BOOKED`). No deadlocks or double bookings.

### 2.4 Level 4: Integration & Protocol Fuzzing
- **Fuzz Testing:** Transmit corrupted JSON, incomplete frames, oversized lines (> 16 KiB), and unexpected message types.
- **Acceptance Criterion:** Server replies with `ERROR` (`INVALID_REQUEST_FORMAT` / `UNKNOWN_REQUEST_TYPE`) or terminates abusive connections without crashing the server socket.

---

## 3. Continuous Integration Automation

All pull requests trigger the GitHub Actions workflow defined in `.github/workflows/java-ci.yml`:
1. Clean environment checkout on `ubuntu-latest`.
2. Java 21 Temurin JDK configuration.
3. Execution of `mvn -B clean test`.
4. Build fails if any compilation warning (`-Werror`) or unit test failure occurs.
