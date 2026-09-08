package com.project926.backend.exception;

/**
 * Mirrors the verify route's SOLD_OUT branch: a Razorpay payment was
 * genuinely captured, but confirm_booking_and_commit_inventory could no
 * longer commit inventory (the hold lapsed and someone else's confirmed
 * purchase took the last seat). The existing code marks the payment
 * "paid" anyway (the money is real) and cancels the booking, logging that
 * a manual refund is required — see PaymentService, which reproduces that
 * exact sequence before this exception is thrown to the controller.
 * Mapped to HTTP 409 by GlobalExceptionHandler.
 */
public class SoldOutException extends RuntimeException {

    public SoldOutException(String message) {
        super(message);
    }
}
