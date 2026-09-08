package com.project926.backend.exception;

/**
 * Carries the exact existing error message text for the payment flow's many
 * specific 400 outcomes (e.g. "Only 3 tickets left for VIP", "Event is not
 * available for booking", "Signature verification failed") — unlike most of
 * the rest of the API, these endpoints' existing Next.js implementation
 * returns the literal dynamic message as {@code {error: message}}, not a
 * fixed generic string, so this exception preserves that instead of being
 * mapped to a generic "Invalid request" the way validation failures
 * elsewhere in the API are. Mapped to HTTP 400 by GlobalExceptionHandler.
 */
public class BookingValidationException extends RuntimeException {

    public BookingValidationException(String message) {
        super(message);
    }
}
