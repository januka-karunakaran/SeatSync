# Member 04: synchronization and live seat management

Ownership follows the supplied project PDFs: Member 01 owns the client/UI,
Member 02 NIO/integration, Member 03 workers, Member 04 booking, Member 05 security.
The older ownership table in README/CONTRIBUTIONS differs from these PDFs.

## Implementation

`booking.InMemoryReservationService` implements the existing `ReservationService`.
Each instance starts with SHOW-01, 40 available seats A1-E8, and version 0.
Create **one instance per server**, shared by every worker/client.

One inventory lock protects availability checks, UUID booking reference generation,
reservation ownership/index changes and version increments. Cancellation checks the
owner under that same lock. A cancelled booking reference cannot cancel a later
booking. Snapshot creation also takes the lock and returns immutable data in seat order.
Snapshots expose only the existing opaque user ID needed by the UI, no credentials
or reservation records. Restarting the server resets inventory.

Seat IDs are trimmed and normalized using Locale.ROOT. Unknown show/seat bookings
return SHOW_NOT_FOUND/SEAT_NOT_FOUND. Missing identity returns AUTH_REQUIRED;
duplicate booking returns SEAT_ALREADY_BOOKED. Cancellation returns
BOOKING_NOT_FOUND or NOT_BOOKING_OWNER as appropriate. Rejections change neither
version nor inventory and generate no events. The caller must obtain userId from
the authenticated RequestContext, never from an untrusted request field.

`snapshot(unknownShow)` throws IllegalArgumentException because the frozen snapshot
contract has no error result. Integration must validate GET_SEATS.showId and map
unknown shows to SHOW_NOT_FOUND before calling snapshot. The current M3 processor
maps uncaught runtime exceptions to INTERNAL_ERROR, so this is an integration task.

## Events

Register/unregister with `addSeatChangeListener` / `removeSeatChangeListener`.
Each successful mutation captures a complete immutable 40-seat snapshot and enqueues
it while holding the inventory lock. One caller drains events in commit/version
order, with listener callbacks outside both inventory and publisher locks. Reentrant
mutations enqueue after the current event. Runtime exceptions from a subscriber are
isolated; other subscribers still receive the event and the booking stays committed.

Delivery runs on a calling worker, with no additional executor to shut down. If a
drain is already active, another mutation may return before its event is delivered.
Listeners must enqueue outbound frames quickly: a slow listener delays notifications
and may build a queue, although it does not block inventory access. Removing a listener
prevents future subscriptions; events already queued retain their subscriber list.

## Integration handoff to Member 02

The normal `SeatSyncServerMain` still uses MockRequestDispatcher. This member's
change does not substitute mock credentials for Member 05's authentication service.
After auth is available, wire:

```java
var reservations = new InMemoryReservationService();
var dispatcher = new WorkerRequestDispatcher(authService, reservations);
var server = new NioBookingServer(config, dispatcher);
reservations.addSeatChangeListener((showId, version, seats) -> {
    String json = ProtocolJson.serialize(new SeatUpdatedEvent(showId, version, seats));
    server.getSessionRegistry().getAll().stream()
        .filter(session -> session.isOpen() && session.isAuthenticated())
        .forEach(session -> session.send(json));
});
```

Use existing common protocol classes; do not add SEAT_TAKEN/BOOK_SUCCESS aliases.
`session.send` queues writes for the selector. Do not write directly to a channel
from a worker/listener. Stop accepting/processing requests and close the worker
dispatcher as part of the server shutdown sequence.

`ReservationServerIntegrationTest` demonstrates this wiring over real TCP with the
actual reservation engine and worker dispatcher; authentication alone is a test stub.
Production login integration remains Member 05/Member 02 work.

The real TCP test exposed a shared protocol defect: Jackson's PROPERTY type
discriminator and getType() both emitted a `type` field, which M3's strict duplicate-key
parser rejected. Message now uses EXISTING_PROPERTY so the wire carries one `type`.
A shared contract regression test checks strict parsing and polymorphic round trips.
This small cross-package fix is required for the existing client and worker to interoperate.

## Verification and oral demonstration

```powershell
mvn "-Dtest=InMemoryReservationServiceTest,ReservationConcurrencyTest,SeatEventPublisherTest,ReservationServerIntegrationTest" test
mvn clean test
```

Tests cover initial state, invalid input, ownership, stale cancellation references,
immutable snapshots, unique booking IDs, ten contenders with exactly one winner,
different-seat concurrency, cancel/book overlap, two subscribers, ordered events,
listener failure/reentrancy and inventory progress while a subscriber is blocked.
Concurrency starts with latches rather than timing sleeps.

Verified on 22 November 2026 with Maven 3.9.11 / Java 21 (release target 17):
37 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS. This includes 13 booking
tests and the additional shared protocol regression test. The ten-contender test
asserted one successful booking and nine SEAT_ALREADY_BOOKED results. The TCP test
verified BOOK_OK, BOOK_REJECTED, NOT_BOOKING_OWNER, cancellation, rebooking and
versions 1-3 reaching both subscribers.

The environment's default Maven repository resolved to an inaccessible C:\.m2 path.
Validation used a temporary local cache (existing cached artifacts copied before
downloading missing dependencies), without changing pom.xml or machine settings:

```powershell
$seatSyncCache = Join-Path $env:TEMP 'seatsync-maven-repository'
mvn -B -o "-Dmaven.repo.local=$seatSyncCache" clean test
```

For the two-minute oral: show the atomic section in book(), run
ReservationConcurrencyTest (one success, nine rejections), explain what would race
without the lock, then show cancellation and two-client updates using
ReservationServerIntegrationTest. Keep test output and screenshots with the report.

Delivery follows the playbook: PR02 feature/02-seat-engine (review M3, merge M2),
then PR08 feature/08-seat-broadcast (review/merge M2) after integration. The local
implementation includes both engine and event support for review; no PR is created
or merged automatically.
