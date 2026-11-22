package com.seatsync.common.model;

/** Structured business rejection from an authoritative admin transaction. */
public final class AdminOperationException extends RuntimeException {
    private final ErrorCode errorCode;
    public AdminOperationException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    public ErrorCode errorCode() { return errorCode; }
}
