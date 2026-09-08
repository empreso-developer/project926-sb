package com.project926.backend.exception;

/**
 * Mirrors the verify route's generic confirmErr branch exactly: unlike
 * create-order (which surfaces the RPC's raw message) and the SOLD_OUT
 * branch, any OTHER error from confirm_booking_and_commit_inventory is
 * swallowed into a fixed generic message — {@code {error: 'Failed to
 * confirm booking'}}, HTTP 500 — never the raw RPC text. Mapped by
 * GlobalExceptionHandler.
 */
public class BookingConfirmationException extends RuntimeException {

    public BookingConfirmationException(String rpcMessage) {
        super(rpcMessage); // kept for server-side logging only, never returned to the client
    }
}
