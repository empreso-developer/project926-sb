package com.project926.backend.exception;

/**
 * Carries the exact existing message text for the payment flow's 404
 * outcomes ("Event not found", "Booking not found") — see
 * BookingValidationException's Javadoc for why this flow preserves literal
 * message text rather than a fixed generic string. Mapped to HTTP 404 by
 * GlobalExceptionHandler.
 */
public class PaymentFlowNotFoundException extends RuntimeException {

    public PaymentFlowNotFoundException(String message) {
        super(message);
    }
}
