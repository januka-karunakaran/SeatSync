package com.seatsync.common.contracts;

/**
 * Pluggable time provider contract for TTL expiration and deterministic testing.
 */
@FunctionalInterface
public interface ClockService {

    /**
     * @return Current time in milliseconds from unix epoch
     */
    long currentTimeMillis();

    /**
     * Standard wall clock implementation based on {@link System#currentTimeMillis()}.
     */
    static ClockService system() {
        return System::currentTimeMillis;
    }
}
