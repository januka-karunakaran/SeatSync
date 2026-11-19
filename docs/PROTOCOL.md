# SeatSync Wire Protocol & Shared Contracts Specification

## 1. Framing and Transport Rules

1. **Transport:** Persistent TCP socket connection on default port `8080`.
2. **Encoding:** UTF-8 character encoding throughout all message payloads.
3. **Delimitation:** Exactly one JSON object per line, terminated by a single Line Feed character (`\n`, ASCII 0x0A).
4. **Frame Size Limit:** Maximum **16 KiB** (16,384 bytes) per frame. If an incoming client stream exceeds 16,384 bytes without an LF delimiter, the server must reject the payload and close the connection immediately to protect against buffer overflow or memory denial-of-service.
5. **Correlation ID:** Every client request and every server reply **MUST** carry a non-null string `requestId`. Replies echo the identical `requestId` from the client request they answer.
6. **Asynchronous Broadcast Events:** Server-initiated push events (`SEAT_UPDATED`) do **NOT** carry a `requestId`.
7. **Actor Trust Principle:** Client request payloads **NEVER** contain or specify a `userId`. All operations (`BOOK_SEAT`, `CANCEL_BOOKING`) strictly infer the actor from the authenticated `RequestContext` bound to the underlying socket channel. Client attempts to pass a user identifier are forbidden.

---

## 2. Protocol Message Catalog

### 2.1 Client Request Messages

#### 2.1.1 LOGIN
Initiates authentication.
```json
{
  "requestId": "req-001",
  "type": "LOGIN",
  "username": "alice",
  "password": "password123"
}
```

#### 2.1.2 GET_SEATS
Requests the current seat allocation snapshot for a show.
```json
{
  "requestId": "req-002",
  "type": "GET_SEATS",
  "showId": "SHOW-01"
}
```

#### 2.1.3 BOOK_SEAT
Attempts to reserve a seat. Identity is inferred from the authenticated session.
```json
{
  "requestId": "req-003",
  "type": "BOOK_SEAT",
  "showId": "SHOW-01",
  "seatId": "C4"
}
```

#### 2.1.4 CANCEL_BOOKING
Attempts to cancel a booking. The server verifies ownership via session identity.
```json
{
  "requestId": "req-004",
  "type": "CANCEL_BOOKING",
  "bookingId": "BKG-78192"
}
```

#### 2.1.5 LOGOUT
Terminates the authenticated session state.
```json
{
  "requestId": "req-005",
  "type": "LOGOUT"
}
```

---

### 2.2 Server Reply Messages

#### 2.2.1 LOGIN_OK
Sent when authentication succeeds.
```json
{
  "requestId": "req-001",
  "type": "LOGIN_OK",
  "userId": "USR-101",
  "username": "alice"
}
```

#### 2.2.2 LOGIN_FAILED
Sent when authentication fails.
```json
{
  "requestId": "req-001",
  "type": "LOGIN_FAILED",
  "errorCode": "INVALID_CREDENTIALS",
  "message": "Username or password incorrect."
}
```

#### 2.2.3 SEAT_SNAPSHOT
Returns the complete list of 40 seats (`A1`–`E8`) for the show.
```json
{
  "requestId": "req-002",
  "type": "SEAT_SNAPSHOT",
  "showId": "SHOW-01",
  "version": 14,
  "seats": [
    { "seatId": "A1", "status": "AVAILABLE", "bookedBy": null },
    { "seatId": "A2", "status": "BOOKED", "bookedBy": "USR-102" }
  ]
}
```

#### 2.2.4 BOOK_OK
Sent when a seat booking succeeds.
```json
{
  "requestId": "req-003",
  "type": "BOOK_OK",
  "bookingId": "BKG-99214",
  "showId": "SHOW-01",
  "seatId": "C4",
  "message": "Seat C4 booked successfully."
}
```

#### 2.2.5 BOOK_REJECTED
Sent when a seat booking is rejected.
```json
{
  "requestId": "req-003",
  "type": "BOOK_REJECTED",
  "errorCode": "SEAT_ALREADY_BOOKED",
  "message": "Seat C4 is already reserved by another user."
}
```

#### 2.2.6 CANCEL_OK
Sent when a booking is successfully cancelled.
```json
{
  "requestId": "req-004",
  "type": "CANCEL_OK",
  "bookingId": "BKG-99214",
  "seatId": "C4",
  "message": "Booking BKG-99214 cancelled successfully."
}
```

#### 2.2.7 CANCEL_REJECTED
Sent when a booking cancellation is rejected.
```json
{
  "requestId": "req-004",
  "type": "CANCEL_REJECTED",
  "errorCode": "NOT_BOOKING_OWNER",
  "message": "User does not own booking BKG-99214."
}
```

#### 2.2.8 LOGOUT_OK
Sent when logout completes.
```json
{
  "requestId": "req-005",
  "type": "LOGOUT_OK",
  "message": "Session terminated successfully."
}
```

#### 2.2.9 ERROR
Generic protocol error reply.
```json
{
  "requestId": "req-099",
  "type": "ERROR",
  "errorCode": "INVALID_REQUEST_FORMAT",
  "message": "Malformed JSON frame received."
}
```

---

### 2.3 Server Asynchronous Event Messages

#### 2.3.1 SEAT_UPDATED
Broadcast to all connected clients whenever seat status changes. Does not carry a `requestId`.
```json
{
  "type": "SEAT_UPDATED",
  "showId": "SHOW-01",
  "version": 15,
  "seats": [
    { "seatId": "C4", "status": "BOOKED", "bookedBy": "USR-101" }
  ]
}
```

---

## 3. Server-Side Error Codes Dictionary

| ErrorCode | HTTP Analog | Description |
| :--- | :--- | :--- |
| `AUTH_REQUIRED` | 401 Unauthorized | Attempted operation requiring authentication on an unauthenticated session. |
| `INVALID_CREDENTIALS` | 401 Unauthorized | Bad username or password supplied during `LOGIN`. |
| `ALREADY_AUTHENTICATED` | 409 Conflict | Session has already been authenticated. |
| `SEAT_NOT_FOUND` | 404 Not Found | Requested seatId is outside the valid range `A1`–`E8`. |
| `SEAT_ALREADY_BOOKED` | 409 Conflict | Requested seat is currently occupied by another booking. |
| `BOOKING_NOT_FOUND` | 404 Not Found | Target booking ID does not exist. |
| `NOT_BOOKING_OWNER` | 403 Forbidden | User attempted to cancel a booking owned by another account. |
| `INVALID_REQUEST_FORMAT`| 400 Bad Request | Payload could not be parsed as valid JSON or is missing required fields. |
| `UNKNOWN_REQUEST_TYPE` | 400 Bad Request | Unrecognized `type` header in client frame. |
| `PAYLOAD_TOO_LARGE` | 413 Payload Too Large| Frame exceeds the 16 KiB limit. |
| `SHOW_NOT_FOUND` | 404 Not Found | Requested show ID is not `SHOW-01`. |
| `RATE_LIMITED` | 429 Too Many Requests| Client request rate exceeds allowed threshold. |
| `INTERNAL_ERROR` | 500 Internal Error | Unhandled server runtime exception. |

---

## 4. Shared Contract Signatures & Package Definitions

All contracts reside in package `com.seatsync.common.contracts`.

### 4.1 ReservationService
```java
package com.seatsync.common.contracts;

import com.seatsync.common.model.BookingResult;
import com.seatsync.common.model.CancelResult;
import com.seatsync.common.model.SeatSnapshot;

public interface ReservationService {
    BookingResult book(String userId, String showId, String seatId);
    CancelResult cancel(String userId, String bookingId);
    SeatSnapshot snapshot(String showId);
}
```

### 4.2 AuthService
```java
package com.seatsync.common.contracts;

import com.seatsync.common.model.AuthResult;

public interface AuthService {
    AuthResult login(String username, char[] password);
}
```

### 4.3 RequestContext
```java
package com.seatsync.common.contracts;

import java.util.Optional;

public interface RequestContext {
    String getSessionId();
    Optional<String> getAuthenticatedUserId();
    default boolean isAuthenticated() {
        return getAuthenticatedUserId().isPresent();
    }
    void setAuthenticatedUserId(String userId);
    void clearAuthentication();
    void send(String jsonLine); // Thread-safe outbound frame transmission
    boolean isOpen();
    void close();
}
```

### 4.4 RequestDispatcher
```java
package com.seatsync.common.contracts;

public interface RequestDispatcher {
    void dispatch(RequestContext context, String jsonLine);
}
```

### 4.5 SeatChangeListener
```java
package com.seatsync.common.contracts;

import com.seatsync.common.model.SeatView;
import java.util.List;

@FunctionalInterface
public interface SeatChangeListener {
    void onSeatUpdated(String showId, long version, List<SeatView> seats);
}
```
