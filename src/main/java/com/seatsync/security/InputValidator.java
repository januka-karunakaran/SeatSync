package com.seatsync.security;

import com.seatsync.common.model.ProtocolConstants;

public final class InputValidator {
    private InputValidator() {}

    public static boolean username(String value) {
        return value != null && value.matches("[a-zA-Z0-9_]{1,32}");
    }

    public static boolean seatId(String value) {
        return value != null && ProtocolConstants.VALID_SEAT_IDS.contains(value);
    }

    public static boolean bookingId(String value) {
        return value != null && value.matches("BKG-[A-Za-z0-9-]{1,64}");
    }
}
