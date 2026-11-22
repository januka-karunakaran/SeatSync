# Admin protocol amendment (user-authorized)

The final PDF's admin commands were missing from U00. The user explicitly requested
their shared DTO and authoritative booking API implementation. This additive amendment
retains all old v1/v2 command names and ShowDetails/ReservationService signatures.
SecurityRequestDispatcher still rejects client-supplied actor/userId/role/isAdmin fields.

| Request | Required payload (besides type/requestId) | Reply | Permission |
| --- | --- | --- | --- |
| ADMIN_CREATE_MOVIE | movie: MovieDetails | ADMIN_MOVIE_CREATED | ADMIN |
| ADMIN_CREATE_SHOW | movieId, showDetails: ShowDetails | ADMIN_SHOW_ADDED | ADMIN |
| ADMIN_DISABLE_SHOW | showId | ADMIN_SHOW_DISABLED | ADMIN |
| ADMIN_LIST_BOOKINGS | showId, pageNumber, pageSize | ADMIN_BOOKINGS_REPLY | ADMIN |
| GET_MOVIES | pageNumber, pageSize | MOVIE_LIST_REPLY | Authenticated |

```json
{"type":"ADMIN_CREATE_MOVIE","requestId":"m1","movie":{"movieId":"MOVIE-03","title":"Evening Film","description":"A new release","genre":"Drama","durationMinutes":90}}
{"type":"ADMIN_MOVIE_CREATED","requestId":"m1","movieId":"MOVIE-03","message":"Movie created"}
{"type":"ADMIN_CREATE_SHOW","requestId":"s1","movieId":"MOVIE-03","showDetails":{"showId":"SHOW-03","title":"Evening Film","description":"A new release","genre":"Drama","startTime":"2030-01-01T18:00:00Z","endTime":"2030-01-01T20:00:00Z","venue":"New Room","layout":{"layoutId":"L3","name":"New Room","rowCount":5,"columnCount":8,"totalCapacity":40,"tiers":[]}}}
{"type":"ADMIN_DISABLE_SHOW","requestId":"d1","showId":"SHOW-03"}
{"type":"ADMIN_LIST_BOOKINGS","requestId":"b1","showId":"SHOW-03","pageNumber":0,"pageSize":20}
{"type":"ADMIN_BOOKINGS_REPLY","requestId":"b1","bookings":{"items":[{"bookingId":"BKG-example","showId":"SHOW-03","seatId":"A1","userId":"demo-alice"}],"pageNumber":0,"pageSize":20,"totalItems":1,"hasMore":false}}
{"type":"GET_MOVIES","requestId":"movies","pageNumber":0,"pageSize":5}
```

The ADMIN_CREATE_SHOW reply reuses ADMIN_SHOW_ADDED. Authoritative title/description/
genre are copied from the stored movie. Show windows must start in the future, cover
the movie duration, and not overlap another enabled show at the same trimmed,
case-insensitive venue. ADMIN_ADD_SHOW remains for legacy callers without movie links.
Layouts contain at most 120 seats. The demo catalog allows 10 shows and 1000 movies.

Movie pages allow 1-5 records; active-booking pages 1-20. Nonnegative page numbers
use long offset arithmetic. Pages are immutable per-request snapshots, sorted by
booking ID for bookings; concurrent mutations can move entries between page requests.
Administrative user IDs are never exposed through public movie/catalog replies.

Disabling hides the show from GET_SHOWS and rejects new bookings, holds and waitlist
joins with SHOW_DISABLED. It revokes active holds/private offers and clears queued
users atomically. Confirmed bookings remain visible to admins and cancellable by
their owners. GET_SHOW_DETAILS remains available for historical reference. Repeated
disable returns INVALID_STATE; unknown show returns SHOW_NOT_FOUND.

SHOW_UPDATED_EVENT adds `enabled` (boolean), and LOGIN_OK adds `role` (UserRole).
Old payloads without these fields deserialize to enabled=true / CUSTOMER. Old Java
constructors remain. Roles are resolved from server accounts and never accepted in
requests. Catalog lifecycle callbacks are queued under the inventory lock and delivered
after it is released; callbacks cannot undo committed state.

ADMIN_UPDATE_SEAT_STATUS now runs an atomic source transaction: BOOKED creates an
admin-owned reservation on a free seat; HELD creates a normal 120-second admin hold;
AVAILABLE removes that seat's active reservation/hold indexes. Allocating an occupied
seat or freeing when users are queued returns INVALID_STATE. Normal CANCEL_BOOKING /
ADMIN_CANCEL_HOLD preserve FIFO promotion and are the appropriate actions for those
queues. Any subsequent cancellation using a removed booking ID returns BOOKING_NOT_FOUND.

New business error codes: MOVIE_ALREADY_EXISTS, MOVIE_NOT_FOUND, SHOW_DISABLED and
INVALID_STATE. Existing duplicate show, unknown show, forbidden role and paging error
codes remain. Errors never include request bodies or secrets.

AdminCatalogOperations is the internal trusted backend API. It uses the existing
inventory lock, maps and event publisher. Only AdminService/AdminRequestDispatcher
should expose it to socket clients; the backend is not a separate authentication boundary.
