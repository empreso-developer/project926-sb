package com.project926.backend.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.UUID;

/**
 * Mirrors the existing verify route's success response exactly:
 * {@code { success, bookingId, reference, qrCode }}.
 *
 * qrCode is always null in Phase D: QR code generation is explicitly out of
 * scope for this phase (see the migration brief) and confirm_booking_and_
 * commit_inventory's p_qr_code parameter is passed null accordingly — the
 * bookings.qr_code column is nullable so this is a valid, non-breaking
 * value, not a schema change. The field NAME is preserved for contract
 * parity even though its VALUE will always be null until Phase E adds real
 * QR generation. See PaymentService and the Phase D report for the full
 * explanation of this deliberate, disclosed scope boundary.
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record VerifyPaymentResponse(
    boolean success,
    UUID bookingId,
    String reference,
    String qrCode
) {
}
