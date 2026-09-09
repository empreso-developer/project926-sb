package com.project926.backend.dto;

/**
 * The subset of Razorpay's {@code payment.entity} webhook object actually
 * used by RazorpayWebhookService (Phase G) — not a full representation of
 * every field Razorpay sends. Field names are plain camelCase; the
 * application-wide Jackson SNAKE_CASE naming strategy (application.yml)
 * already maps {@code orderId} to/from the wire field {@code order_id},
 * which happens to be exactly Razorpay's own field name — no
 * {@code @JsonProperty} overrides needed.
 *
 * @param id       the Razorpay payment id (e.g. "pay_xxx") — used as
 *                 razorpay_payment_id when marking the local payment paid.
 * @param orderId  the Razorpay order id (e.g. "order_xxx") — the sole
 *                 correlation key back to Project926's {@code payments}
 *                 table (see RazorpayWebhookService's Javadoc).
 * @param amount   amount in paise, as sent by Razorpay.
 * @param currency e.g. "INR".
 * @param status   e.g. "captured", "failed" — cross-checked against the
 *                 top-level event name, never trusted alone.
 */
public record RazorpayPaymentEntity(
    String id,
    String orderId,
    Long amount,
    String currency,
    String status
) {
}
