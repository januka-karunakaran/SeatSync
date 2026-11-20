package com.seatsync.server;

/**
 * Standard bootstrap alias for {@link SeatSyncServerMain}.
 * Ensures scripts and Maven exec plugin profiles can invoke either class name.
 */
public final class SeatSyncServer {

    private SeatSyncServer() {
        // utility class
    }

    public static void main(String[] args) {
        SeatSyncServerMain.main(args);
    }
}
