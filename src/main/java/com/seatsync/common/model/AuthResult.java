package com.seatsync.common.model;

/**
 * Result of an authentication request.
 *
 * @param success True if authentication succeeded
 * @param userId Unique server-assigned user identifier (null if authentication failed)
 * @param username Authenticated username (null if authentication failed)
 * @param message Human-readable status message
 * @param errorCode Specific server error code on failure (null on success)
 */
public record AuthResult(
    boolean success,
    String userId,
    String username,
    String message,
    ErrorCode errorCode
) {
    public static AuthResult ok(String userId, String username, String message) {
        return new AuthResult(true, userId, username, message, null);
    }

    public static AuthResult failed(ErrorCode errorCode, String message) {
        return new AuthResult(false, null, null, message, errorCode);
    }
}
