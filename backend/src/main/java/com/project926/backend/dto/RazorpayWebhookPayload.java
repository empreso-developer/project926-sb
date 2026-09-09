package com.project926.backend.dto;

/**
 * Mirrors Razorpay's top-level webhook {@code payload} object. Only the
 * {@code payment} container is modeled — {@code order}/{@code refund}
 * containers that some other Razorpay events carry are not needed for the
 * two events this phase handles (payment.captured, payment.failed both
 * carry a payment entity — see RazorpayWebhookService).
 */
public record RazorpayWebhookPayload(
    RazorpayPaymentContainer payment
) {
}
