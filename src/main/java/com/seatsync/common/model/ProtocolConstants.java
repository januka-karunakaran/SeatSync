package com.seatsync.common.model;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Common protocol and domain constants shared across server and client.
 */
public final class ProtocolConstants {

    private ProtocolConstants() {
        // utility class
    }

    /**
     * Wire framing charset: UTF-8.
     */
    public static final Charset CHARSET = StandardCharsets.UTF_8;

    /**
     * Maximum allowed size for a single message frame in bytes: 16 KiB (16,384 bytes).
     * Connections exceeding this limit without a newline will be terminated.
     */
    public static final int MAX_FRAME_BYTES = 16 * 1024;

    /**
     * Message line delimiter.
     */
    public static final String LINE_DELIMITER = "\n";

    /**
     * Canonical single-show identifier for the sprint.
     */
    public static final String DEFAULT_SHOW_ID = "SHOW-01";

    /**
     * Canonical server listening port.
     */
    public static final int DEFAULT_PORT = 8080;

    /**
     * All 40 valid seat identifiers from A1 to E8 (5 rows: A-E, 8 seats each).
     */
    public static final Set<String> VALID_SEAT_IDS;

    static {
        Set<String> seats = new LinkedHashSet<>(40);
        char[] rows = {'A', 'B', 'C', 'D', 'E'};
        for (char row : rows) {
            for (int col = 1; col <= 8; col++) {
                seats.add(String.valueOf(row) + col);
            }
        }
        VALID_SEAT_IDS = Collections.unmodifiableSet(seats);
    }

    /**
     * Validates whether a given seat ID belongs to the canonical A1-E8 set.
     *
     * @param seatId seat identifier candidate
     * @return true if valid
     */
    public static boolean isValidSeatId(String seatId) {
        return seatId != null && VALID_SEAT_IDS.contains(seatId.trim().toUpperCase());
    }
}
