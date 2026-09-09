package com.project926.backend.dto;

/**
 * Mirrors the customer dashboard's {@code payment.razorpay_payment_id}
 * usage exactly. Deliberately a nullable NESTED object, not a bare
 * nullable string, to preserve two distinct states the original code
 * distinguished:
 * <ul>
 *   <li>no payment row exists at all for this booking (possible — see
 *       PaymentRepository#findByBookingIdIn's Javadoc) — the whole
 *       "Payment: ..." line is omitted, matching the original's
 *       {@code {payment && (...)}} guard.</li>
 *   <li>a payment row exists but {@code razorpay_payment_id} is still
 *       null (payment created but not yet verified) — the line IS shown,
 *       displaying "—", matching the original's
 *       {@code payment.razorpay_payment_id?.slice(0,16) ?? '—'}.</li>
 * </ul>
 */
public record CustomerBookingPaymentDto(
    String razorpayPaymentId
) {
}
