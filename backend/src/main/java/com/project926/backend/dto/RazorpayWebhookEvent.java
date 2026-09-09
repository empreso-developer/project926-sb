package com.project926.backend.dto;

/**
 * The top-level shape of a Razorpay webhook delivery, restricted to the
 * fields this phase actually reads: {@code event} (e.g.
 * "payment.captured") and {@code payload.payment.entity}. Parsed ONLY
 * after {@link com.project926.backend.integration.razorpay.RazorpayWebhookSignatureVerifier}
 * has verified the raw request body — see RazorpayWebhookController.
 */
public record RazorpayWebhookEvent(
    String event,
    RazorpayWebhookPayload payload
) {
}
