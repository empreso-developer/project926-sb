package com.project926.backend.exception;

/**
 * Mirrors sendTicketConfirmationEmail's `if (error) throw new Error(...)` —
 * any Resend API failure (non-2xx response, network error, malformed
 * response). Caught by TicketEmailService, never allowed to propagate to
 * the payment-verification response (see that class's Javadoc). Never
 * carries the API key or authorization header in its message.
 */
public class ResendSendException extends RuntimeException {

    public ResendSendException(String message) {
        super(message);
    }

    public ResendSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
