package com.project926.backend.exception;

/**
 * Mirrors the verify route's `catch (fetchErr)` around razorpay.payments.fetch:
 * "Could not verify payment with Razorpay", HTTP 502. Never carries the
 * underlying RazorpayException's message verbatim into the client response
 * (that's logged server-side only), matching the existing code, which also
 * doesn't leak the raw SDK error to the client.
 */
public class RazorpayGatewayException extends RuntimeException {

    public RazorpayGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
