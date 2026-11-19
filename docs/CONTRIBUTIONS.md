# SeatSync Team Contributions & Workflow Guide

## 1. Project Context & Team Roster

SeatSync is an undergraduate real-time distributed systems assignment developed across a strict 4-day implementation sprint.

- **Internal Code Freeze:** 23 Nov 2026, 23:59
- **Moodle Submission Deadline:** 26 Nov 2026, 23:59
- **Reviewer & Merger of Record:** Member 01

---

## 2. Team Member Ownership Matrix

| Member | Focus Area | Package Ownership | Key Deliverables |
| :--- | :--- | :--- | :--- |
| **Member 01** | Architecture, Java NIO Server Socket & Selector Multiplexer | `com.seatsync.server`, `com.seatsync.network` (server-side) | `ServerSocketChannel` selector loop, `SelectionKey` handling, connection life-cycle, PR reviewing & merging. |
| **Member 02** | Protocol Contracts, Serialization, Session Abstraction *(PR00 Author)* | `com.seatsync.common.*` | Immutable DTO records, `ProtocolJson` parser, `RequestContext`, 16 KiB frame enforcement, `ProtocolContractTest`. |
| **Member 03** | In-Memory Booking Engine & Concurrency Control | `com.seatsync.booking`, `com.seatsync.concurrency` | `ReservationService` implementation, `ExecutorService` worker pool, seat locking (`A1`–`E8`), snapshot generation. |
| **Member 04** | Authentication, User Sessions & Security Validation | `com.seatsync.security` | `AuthService` implementation, credentials store, session token management, input validation, rate limiting. |
| **Member 05** | Client Network Layer & Swing GUI Presentation | `com.seatsync.client`, `com.seatsync.ui`, `com.seatsync.network` (client-side) | `java.net.Socket` streaming client, Swing interactive 40-seat grid, real-time `SEAT_UPDATED` push rendering. |

---

## 3. Git Branching Strategy & PR Workflow

### 3.1 Branch Naming Convention
- Foundation Bootstrap: `chore/00-bootstrap`
- Feature Branches:
  - `feature/01-nio-server` (Member 01)
  - `feature/03-booking-concurrency` (Member 03)
  - `feature/04-auth-security` (Member 04)
  - `feature/05-swing-client` (Member 05)
- Bug Fixes: `fix/<issue-description>`

### 3.2 PR Review Policy
1. **Target Branch:** All pull requests target `main`.
2. **Reviewer Mandate:** Every PR must be reviewed and merged by **Member 01**.
3. **No Self-Merging:** Authors are strictly forbidden from merging their own PRs.
4. **CI Gate:** Every PR must pass the automated GitHub Actions CI workflow (`mvn -B clean test` on Java 21) before merge approval.
5. **No Secrets/Build Artifacts:** `.gitignore` enforces exclusion of `target/`, IDE files, and credentials. Never commit secrets.

---

## 4. Milestone Schedule (4-Day Sprint)

```
[Day 1 - 20 Nov 2026] ──> PR00: Bootstrap Foundation & Contracts (Member 02)
                          Review & Merge by Member 01

[Day 2 - 21 Nov 2026] ──> PR01: Java NIO Server Socket & Frame Parser (Member 01)
                      ──> PR04: In-Memory AuthService & Security (Member 04)

[Day 3 - 22 Nov 2026] ──> PR03: Concurrency Engine & ReservationService (Member 03)
                      ──> PR05: Swing Desktop Client & TCP Network Adapter (Member 05)

[Day 4 - 23 Nov 2026] ──> End-to-End Integration, Stress Testing & Bug Fixes
                      ──> INTERNAL CODE FREEZE (23 Nov 2026, 23:59)

[Buffer - 24-25 Nov]  ──> Report drafting, user manual, and code quality review.
[Final - 26 Nov 2026] ──> Final Moodle Submission.
```
