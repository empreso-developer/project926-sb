package com.project926.backend.dto;

/** Mirrors Razorpay's {@code payload.payment} wrapper: {@code { entity: {...} }}. */
public record RazorpayPaymentContainer(
    RazorpayPaymentEntity entity
) {
}
