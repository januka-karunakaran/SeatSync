# SeatSync 2.0 Wire Protocol Specification

> An additive, user-authorized Member 04 amendment is documented in
> [V2_ADMIN_PROTOCOL.md](V2_ADMIN_PROTOCOL.md): movie/show creation, show disabling,
> admin booking pages, movie browsing, role-bearing login and lifecycle event flags.

## 1. Framing & Transport Rules (Preserved from V1)

1. **Wire Framing:** UTF-8 character encoding throughout all payloads.
2. **Line Delimitation:** Exactly one JSON object per line terminated by a single Line Feed character (`\n`, ASCII 0x0A). Optional preceding `\r` (CRLF) is accepted and stripped.
3. **Frame Ceiling:** Maximum **16 KiB** (16,384 bytes) per frame. Frames exceeding this limit trigger immediate connection closure.
4. **Correlation Identifiers:** All client requests and all server replies **MUST** carry a non-null string `requestId`. Replies echo the identical `requestId` from the client request. Server-initiated asynchronous events do **NOT** carry a `requestId`.
5. **Session-Bound Identity Invariant:** Client request payloads **NEVER** supply or dictate `userId`. The server strictly extracts the actor from the authenticated `RequestContext` bound to the socket.

---

## 2. Canonical JSON Message Specification

### 2.1 Multi-Show Catalog Messages

#### 2.1.1 GET_SHOWS (Client Request)
```json
{"requestId":"req-c01","type":"GET_SHOWS"}
```

#### 2.1.2 SHOW_LIST_REPLY (Server Reply)
```json
{"requestId":"req-c01","type":"SHOW_LIST_REPLY","shows":[{"showId":"SHOW-01","title":"The Phantom of the Opera","startTime":"2026-12-01T19:30:00Z","venue":"Grand Stage","totalSeats":40,"availableSeats":28},{"showId":"SHOW-02","title":"Les Misérables","startTime":"2026-12-02T20:00:00Z","venue":"Main Hall","totalSeats":120,"availableSeats":115}]}
```

#### 2.1.3 GET_SHOW_DETAILS (Client Request)
```json
{"requestId":"req-c02","type":"GET_SHOW_DETAILS","showId":"SHOW-01"}
```

#### 2.1.4 SHOW_DETAILS_REPLY (Server Reply)
```json
{"requestId":"req-c02","type":"SHOW_DETAILS_REPLY","showDetails":{"showId":"SHOW-01","title":"The Phantom of the Opera","description":"Classic Broadway musical","genre":"Musical","startTime":"2026-12-01T19:30:00Z","endTime":"2026-12-01T22:00:00Z","venue":"Grand Stage","layout":{"layoutId":"LAYOUT-01","name":"Orchestra Hall","rowCount":5,"columnCount":8,"totalCapacity":40,"tiers":[{"tierName":"Standard Row","tierType":"STANDARD","priceInCents":2500,"description":"Standard auditorium seat"}]}}}
```

---

### 2.2 Two-Phase Seat Hold & Confirmation Messages

#### 2.2.1 HOLD_SEAT (Client Request)
```json
{"requestId":"req-h01","type":"HOLD_SEAT","showId":"SHOW-01","seatId":"B4","holdDurationSeconds":120}
```

#### 2.2.2 HOLD_OK (Server Reply)
```json
{"requestId":"req-h01","type":"HOLD_OK","holdId":"HLD-9811","showId":"SHOW-01","seatId":"B4","expiresAt":1795140120000,"message":"Hold confirmed for 120s"}
```

#### 2.2.3 HOLD_REJECTED (Server Reply)
```json
{"requestId":"req-h01","type":"HOLD_REJECTED","showId":"SHOW-01","seatId":"B4","errorCode":"SEAT_ALREADY_HELD","message":"Seat currently held by another user"}
```

#### 2.2.4 RELEASE_HOLD (Client Request)
```json
{"requestId":"req-h02","type":"RELEASE_HOLD","holdId":"HLD-9811"}
```

#### 2.2.5 HOLD_RELEASED_OK (Server Reply)
```json
{"requestId":"req-h02","type":"HOLD_RELEASED_OK","holdId":"HLD-9811","showId":"SHOW-01","seatId":"B4","message":"Hold released"}
```

#### 2.2.6 HOLD_RELEASED_REJECTED (Server Reply)
```json
{"requestId":"req-h02","type":"HOLD_RELEASED_REJECTED","holdId":"HLD-9811","errorCode":"NOT_HOLD_OWNER","message":"User is not hold owner"}
```

#### 2.2.7 CONFIRM_HOLD (Client Request)
```json
{"requestId":"req-h03","type":"CONFIRM_HOLD","holdId":"HLD-9811","idempotencyKey":"idem-uuid-992384"}
```

#### 2.2.8 CONFIRM_OK (Server Reply)
```json
{"requestId":"req-h03","type":"CONFIRM_OK","bookingId":"BKG-5501","ticketId":"TCK-8812","showId":"SHOW-01","seatId":"B4","message":"Booking confirmed"}
```

#### 2.2.9 CONFIRM_REJECTED (Server Reply)
```json
{"requestId":"req-h03","type":"CONFIRM_REJECTED","holdId":"HLD-9811","errorCode":"HOLD_EXPIRED","message":"Seat hold has expired"}
```

---

### 2.3 Waitlist Messages

#### 2.3.1 JOIN_WAITLIST (Client Request)
```json
{"requestId":"req-w01","type":"JOIN_WAITLIST","showId":"SHOW-01"}
```

#### 2.3.2 WAITLIST_JOINED_OK (Server Reply)
```json
{"requestId":"req-w01","type":"WAITLIST_JOINED_OK","waitlistId":"WTL-001","showId":"SHOW-01","position":3,"message":"Added to waitlist at position 3"}
```

#### 2.3.3 WAITLIST_JOINED_REJECTED (Server Reply)
```json
{"requestId":"req-w01","type":"WAITLIST_JOINED_REJECTED","showId":"SHOW-01","errorCode":"ALREADY_ON_WAITLIST","message":"User already on waitlist"}
```

#### 2.3.4 LEAVE_WAITLIST (Client Request)
```json
{"requestId":"req-w02","type":"LEAVE_WAITLIST","showId":"SHOW-01"}
```

#### 2.3.5 WAITLIST_LEFT_OK (Server Reply)
```json
{"requestId":"req-w02","type":"WAITLIST_LEFT_OK","showId":"SHOW-01","message":"Removed from waitlist"}
```

#### 2.3.6 GET_WAITLIST_STATUS (Client Request)
```json
{"requestId":"req-w03","type":"GET_WAITLIST_STATUS","showId":"SHOW-01"}
```

#### 2.3.7 WAITLIST_STATUS_REPLY (Server Reply)
```json
{"requestId":"req-w03","type":"WAITLIST_STATUS_REPLY","showId":"SHOW-01","position":2,"totalWaitlistSize":8}
```

---

### 2.4 Digital Tickets & Booking History Messages

#### 2.4.1 GET_TICKET (Client Request)
```json
{"requestId":"req-t01","type":"GET_TICKET","ticketId":"TCK-1001"}
```

#### 2.4.2 TICKET_REPLY (Server Reply)
```json
{"requestId":"req-t01","type":"TICKET_REPLY","ticket":{"ticketId":"TCK-1001","bookingId":"BKG-5501","showId":"SHOW-01","seatId":"C3","userId":"alice","issuedAt":1795140000000,"verificationHash":"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855","priceInCents":3500}}
```

#### 2.4.3 GET_BOOKING_HISTORY (Client Request)
```json
{"requestId":"req-h01","type":"GET_BOOKING_HISTORY","pageNumber":0,"pageSize":20}
```

#### 2.4.4 BOOKING_HISTORY_REPLY (Server Reply)
```json
{"requestId":"req-h01","type":"BOOKING_HISTORY_REPLY","history":{"items":[{"bookingId":"BKG-5501","showId":"SHOW-01","seatId":"C3","showTitle":"The Phantom of the Opera","showTime":"2026-12-01T19:30:00Z","status":"CONFIRMED","timestamp":1795140000000,"ticketId":"TCK-1001"}],"pageNumber":0,"pageSize":20,"totalItems":1,"hasMore":false}}
```

---

### 2.5 Admin & Governance Messages

#### 2.5.1 ADMIN_ADD_SHOW (Client Request)
```json
{"requestId":"req-a01","type":"ADMIN_ADD_SHOW","showDetails":{"showId":"SHOW-03","title":"Hamilton","description":"Revolutionary musical","genre":"History","startTime":"2026-12-05T18:00:00Z","endTime":"2026-12-05T21:00:00Z","venue":"Broadway Arena","layout":{"layoutId":"L-02","name":"Balcony Room","rowCount":4,"columnCount":10,"totalCapacity":40,"tiers":[]}}}
```

#### 2.5.2 ADMIN_SHOW_ADDED (Server Reply)
```json
{"requestId":"req-a01","type":"ADMIN_SHOW_ADDED","showId":"SHOW-03","message":"Show added successfully"}
```

#### 2.5.3 ADMIN_UPDATE_SEAT_STATUS (Client Request)
```json
{"requestId":"req-a02","type":"ADMIN_UPDATE_SEAT_STATUS","showId":"SHOW-01","seatId":"D1","status":"BOOKED"}
```

#### 2.5.4 ADMIN_SEAT_UPDATED (Server Reply)
```json
{"requestId":"req-a02","type":"ADMIN_SEAT_UPDATED","showId":"SHOW-01","seatId":"D1","status":"BOOKED","message":"Seat updated"}
```

#### 2.5.5 ADMIN_CANCEL_HOLD (Client Request)
```json
{"requestId":"req-a03","type":"ADMIN_CANCEL_HOLD","holdId":"HLD-9811","reason":"Administrative override for vip seating"}
```

#### 2.5.6 ADMIN_HOLD_CANCELLED (Server Reply)
```json
{"requestId":"req-a03","type":"ADMIN_HOLD_CANCELLED","holdId":"HLD-9811","message":"Hold cancelled by admin"}
```

#### 2.5.7 ADMIN_GET_AUDIT_LOGS (Client Request)
```json
{"requestId":"req-a04","type":"ADMIN_GET_AUDIT_LOGS","limit":50}
```

#### 2.5.8 ADMIN_AUDIT_LOGS_REPLY (Server Reply)
```json
{"requestId":"req-a04","type":"ADMIN_AUDIT_LOGS_REPLY","logs":[{"logId":"LOG-1","timestamp":1795140000000,"userId":"admin-01","action":"CANCEL_HOLD","resourceId":"HLD-9811","detail":"Emergency cancellation","ipAddress":"127.0.0.1"}]}
```

---

### 2.6 Telemetry & Pub/Sub Messages

#### 2.6.1 GET_SERVER_METRICS (Client Request)
```json
{"requestId":"req-m01","type":"GET_SERVER_METRICS"}
```

#### 2.6.2 SERVER_METRICS_REPLY (Server Reply)
```json
{"requestId":"req-m01","type":"SERVER_METRICS_REPLY","metrics":{"uptimeSeconds":3600,"activeConnections":42,"totalRequests":125000,"totalBookings":890,"activeHolds":12,"waitlistSize":5,"p95LatencyMs":2.4,"errorCount":3}}
```

#### 2.6.3 SUBSCRIBE_EVENTS (Client Request)
```json
{"requestId":"req-s01","type":"SUBSCRIBE_EVENTS","showId":"SHOW-01"}
```

#### 2.6.4 SUBSCRIBE_OK (Server Reply)
```json
{"requestId":"req-s01","type":"SUBSCRIBE_OK","showId":"SHOW-01","message":"Subscribed to events for SHOW-01"}
```

#### 2.6.5 UNSUBSCRIBE_EVENTS (Client Request)
```json
{"requestId":"req-s02","type":"UNSUBSCRIBE_EVENTS","showId":"SHOW-01"}
```

#### 2.6.6 UNSUBSCRIBE_OK (Server Reply)
```json
{"requestId":"req-s02","type":"UNSUBSCRIBE_OK","showId":"SHOW-01","message":"Unsubscribed"}
```

---

### 2.7 Server Asynchronous Push Events (Omit Request ID)

#### 2.7.1 SEAT_HELD_EVENT
```json
{"type":"SEAT_HELD_EVENT","showId":"SHOW-01","seatId":"C4","holdId":"HLD-100","expiresAt":1795140120000,"version":5}
```

#### 2.7.2 SEAT_RELEASED_EVENT
```json
{"type":"SEAT_RELEASED_EVENT","showId":"SHOW-01","seatId":"C4","holdId":"HLD-100","version":6}
```

#### 2.7.3 WAITLIST_PROMOTED_EVENT
```json
{"type":"WAITLIST_PROMOTED_EVENT","showId":"SHOW-01","seatId":"C4","promotedUserId":"usr-bob","holdId":"HLD-101","expiresAt":1795140240000,"version":7}
```

#### 2.7.4 SHOW_UPDATED_EVENT
```json
{"type":"SHOW_UPDATED_EVENT","showId":"SHOW-01","summary":{"showId":"SHOW-01","title":"The Phantom of the Opera","startTime":"2026-12-01T19:30:00Z","venue":"Grand Stage","totalSeats":40,"availableSeats":27},"version":8}
```

#### 2.7.5 METRICS_UPDATED_EVENT
```json
{"type":"METRICS_UPDATED_EVENT","metrics":{"uptimeSeconds":120,"activeConnections":10,"totalRequests":500,"totalBookings":20,"activeHolds":1,"waitlistSize":0,"p95LatencyMs":1.2,"errorCount":0},"timestamp":1795140000000}
```

---

## 3. Error Codes Dictionary

| ErrorCode | HTTP Analog | Description |
| :--- | :--- | :--- |
| `AUTH_REQUIRED` | 401 | Unauthenticated session attempted a protected operation. |
| `INVALID_CREDENTIALS` | 401 | Bad credentials supplied during authentication. |
| `ALREADY_AUTHENTICATED` | 409 | Session is already authenticated. |
| `SEAT_NOT_FOUND` | 404 | Target seat identifier does not exist in auditorium layout. |
| `SEAT_ALREADY_BOOKED` | 409 | Target seat is permanently booked. |
| `BOOKING_NOT_FOUND` | 404 | Specified booking ID does not exist. |
| `NOT_BOOKING_OWNER` | 403 | User attempted to cancel a booking owned by another account. |
| `INVALID_REQUEST_FORMAT`| 400 | Malformed JSON or missing required fields. |
| `UNKNOWN_REQUEST_TYPE` | 400 | Unrecognized `type` header in client frame. |
| `PAYLOAD_TOO_LARGE` | 413 | Frame exceeds 16 KiB limit. |
| `SHOW_NOT_FOUND` | 404 | Target showId not found in catalog. |
| `RATE_LIMITED` | 429 | Request rejected due to worker queue saturation. |
| `INTERNAL_ERROR` | 500 | Unhandled server exception. |
| `SHOW_ALREADY_EXISTS` | 409 | Attempted to add show with duplicate showId. |
| `SEAT_ALREADY_HELD` | 409 | Seat currently held by another user. |
| `HOLD_EXPIRED` | 410 | Attempted to confirm a hold whose TTL has elapsed. |
| `HOLD_NOT_FOUND` | 404 | Specified holdId does not exist. |
| `NOT_HOLD_OWNER` | 403 | Attempted to release or confirm another user's hold. |
| `WAITLIST_FULL` | 429 | Show waitlist has reached maximum capacity. |
| `ALREADY_ON_WAITLIST` | 409 | User is already enqueued on the waitlist for this show. |
| `NOT_ON_WAITLIST` | 404 | User is not on the waitlist for this show. |
| `FORBIDDEN_ROLE` | 403 | User lacks the required role (e.g. non-admin tried admin action). |
| `IDEMPOTENCY_CONFLICT` | 409 | Idempotency key reused with mismatched transaction payload. |
| `INVALID_PAGE_SIZE` | 400 | Requested page size exceeds maximum bound (50). |
| `TICKET_NOT_FOUND` | 404 | Specified ticketId does not exist or user lacks access. |
