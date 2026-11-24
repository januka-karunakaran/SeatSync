# SeatSync desktop client

The Swing client provides Home, Movies, Shows, Seat Map, My Tickets, Waitlist,
and Admin navigation. It uses the v2 request/reply contracts for catalog
browsing, live seat snapshots, seat holds and confirmation/release, waitlist
actions, digital ticket/history, admin movie/show management, audit logs, and
network/server metrics. The Admin navigation and controls are only exposed to
sessions whose server-provided role is `ADMIN`; the server remains the authority
for every privileged action.

The Seat Map retains direct booking/cancellation compatibility and also supports
two-phase holds with a client countdown. Held seats have a distinct appearance,
seat updates and waitlist promotions are reflected from server events, and the
client subscribes to the selected show. Connection, request loading, empty
catalog/history, and error states are shown in the UI.

Swing actions submit TCP work to a dedicated background executor. The login,
UDP discovery, TLS handshake, requests, and logout do not run on the Swing Event
Dispatch Thread. Protocol replies and asynchronous events are applied to Swing
components on the EDT.

Three separate GUI clients (alice, bob, and carol) were manually verified against
the v2 loopback server on 2026-11-24. Carol held A1; Alice and Bob displayed the
live held-seat update without refreshing. Screenshots are in
[docs/evidence](evidence/), and the test conditions and transport limitation are
recorded in the [Member 05 report](MEMBER05_FINAL_REPORT.pdf).

The TLS-required discovery path was also exercised with a temporary local
certificate and explicit truststore. Discovery marked the server TLS-required
and exposed its secure port; a trusted client login succeeded. That run exposed
an idle read timeout inherited from the TLS handshake, which has been cleared
after handshake and is covered by a regression test that waits past the timeout
before making another request.

TLS and LAN discovery configuration are documented in
[CLIENT_TLS_DISCOVERY.md](CLIENT_TLS_DISCOVERY.md). Run `mvn clean test` for the
automated protocol/client suite, including trusted/untrusted TLS and
no-downgrade cases.
