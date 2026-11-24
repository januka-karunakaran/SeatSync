# SeatSync - Real-Time Ticket Booking System

A lightweight, real-time ticket booking system built in Java for a 5-person network programming assignment.

SeatSync provides a Swing desktop client and a Java NIO server for authenticated multi-show browsing, seat holds/bookings, waitlists, digital tickets, administration, LAN discovery, and optional TLS 1.3 transport.

---

## Sprint Deadlines & Milestones

- **Internal Code Freeze:** 23 Nov 2026
- **Final Moodle Submission:** 26 Nov 2026
- **Current Milestone:** M2 (Repository Bootstrap & Shared Contracts)
- **Branch:** `chore/00-bootstrap` -> **Target:** `main`

---

## Tech Stack & Architecture Constraints

To maintain strict alignment with undergraduate network programming core concepts and avoid extraneous abstraction:
- **Language:** Java 21 (Records, Pattern Matching, Sealed Types)
- **Build Tool:** Apache Maven 3.9+
- **Server:** Non-blocking Java NIO (`java.nio.channels.ServerSocketChannel`, `Selector`)
- **Client:** Desktop Swing GUI (`javax.swing`), blocking/streaming TCP socket (`java.net.Socket`)
- **Serialization:** Jackson JSON (`jackson-databind`, `jackson-core`, `jackson-annotations`)
- **Testing:** JUnit 5 (`junit-jupiter`)
- **Concurrency:** `ExecutorService`, atomic primitives, fine-grained thread-safe locks
- **Data Model:** Pure in-memory transactional seat repository (Auditorium: 5 rows `A`–`E`, 8 columns `1`–`8` = 40 seats, Show: `SHOW-01`)
- **Explicit Exclusions:** No Spring Boot, no React/web frontend, no external databases (MongoDB/PostgreSQL), and no third-party payment APIs. Production endpoints should use the configured TLS 1.3 transport.

---

## 5-Person Team Ownership & Roles

| Member | Primary Ownership | Core Deliverables |
| :--- | :--- | :--- |
| **Member 01** | Core Architecture, Java NIO Server Socket Channel, Selector Multiplexer | Non-blocking NIO server loop, selector keys, connection lifecycle, PR Reviewer & Merger |
| **Member 02** | Network Protocol, Shared Contracts, DTOs & Serialization *(Author of PR00)* | Wire framing (UTF-8 LF, 16 KiB limit), immutable DTOs, `RequestContext`, `ProtocolJson`, protocol test suite |
| **Member 03** | Booking Engine & In-Memory Concurrency Control | Thread-safe seat state, `ReservationService` implementation, `ExecutorService` task scheduling, lock manager |
| **Member 04** | Authentication, User Sessions & Security Validation | `AuthService` implementation, credential verification, session-bound authorization, defensive frame validation |
| **Member 05** | Desktop / Presentation Lead | Swing customer/admin UI, JSON/TCP client integration, live events, UDP server discovery, TLS 1.3 client, tickets and documentation |

---

## Prerequisites

1. **Java Development Kit (JDK):** Version 17 (LTS) or 21 (LTS)
2. **Apache Maven:** Version 3.8 or higher
3. **Git:** Configured with user identity

Verify your local environment:
```bash
java -version
mvn -version
```

---

## Quick Start

### 1. Build and Run Unit/Contract Tests
```bash
mvn clean test
```

### 2. Compile and Package
```bash
mvn clean package
```

### 3. Launch the Server
**Option A: Windows Batch Script**
```cmd
scripts\start-server.bat [optional-port, default 8080]
```
**Option B: Maven Exec Plugin**
```bash
mvn exec:java@server
```

### 4. Launch the Desktop Swing Client
**Option A: Windows Batch Script**
```cmd
scripts\start-client.bat [optional-host, default localhost] [optional-port, default 8080]
```
**Option B: Maven Exec Plugin**
```bash
mvn exec:java@client
```

---

## Repository Structure

```
SeatSync/
├── .github/
│   └── workflows/
│       └── java-ci.yml             # GitHub Actions CI running mvn clean test on Java 21
├── docs/
│   ├── ARCHITECTURE.md             # System architecture & component design
│   ├── PROTOCOL.md                 # Complete wire protocol specification & contracts
│   ├── CONTRIBUTIONS.md            # Team role matrix & contribution policies
│   └── TEST_PLAN.md                # Quality assurance & verification strategy
├── scripts/
│   ├── start-server.bat            # Server startup batch launcher
│   └── start-client.bat            # Swing client startup batch launcher
├── src/
│   ├── main/java/com/seatsync/
│   │   ├── common/                 # Shared domain DTOs, protocol messages & contracts (M2)
│   │   │   ├── model/              # Immutable records: SeatView, SeatSnapshot, Results, ErrorCode
│   │   │   ├── protocol/           # Message envelopes, typed requests/replies, ProtocolJson
│   │   │   └── contracts/          # Core interfaces: ReservationService, AuthService, RequestContext
│   │   ├── server/                 # Server bootstrap & lifecycle (M1)
│   │   ├── network/                # NIO selector server transport & client TCP adapter (M1 / M5)
│   │   ├── booking/                # In-memory booking engine & transactional seat state (M3)
│   │   ├── concurrency/            # Thread pools & locking infrastructure (M3)
│   │   ├── security/               # Authentication & session authorization (M4)
│   │   ├── client/                 # Client bootstrap & coordination (M5)
│   │   └── ui/                     # Swing 40-seat grid GUI & user controls (M5)
│   └── test/java/com/seatsync/
│       └── common/
│           └── ProtocolContractTest.java  # Contract verification test suite (5 tests passing)
├── .gitignore
├── pom.xml                         # Maven build definition with Java 21 & Jackson
└── README.md
```

---

## Technical Documentation Links

- [Developer Setup & Team Onboarding Guide](docs/SETUP_GUIDE.md) **(START HERE)**
- [System Architecture Specification](docs/ARCHITECTURE.md)
- [Wire Protocol & Contract Signatures](docs/PROTOCOL.md)
- [Team Ownership & Workflow Guide](docs/CONTRIBUTIONS.md)
- [Verification & Test Plan](docs/TEST_PLAN.md)
- [Desktop client navigation and workflows](docs/CLIENT_UI.md)
- [TLS and UDP server discovery setup](docs/CLIENT_TLS_DISCOVERY.md)
- [Member 05 final report](docs/MEMBER05_FINAL_REPORT.pdf)
- [Member 05 live GUI evidence](docs/evidence/)
