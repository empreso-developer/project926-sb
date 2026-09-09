package com.project926.backend.dto;

import java.util.UUID;

/**
 * Mirrors the existing check-in route's zod {@code Body} schema exactly:
 * {@code { booking_id?: uuid, reference?: string, event_id?: string }} —
 * at least one of booking_id/reference required (validated in
 * CheckInController, not via bean validation, to produce the existing
 * route's exact {@code {success:false,status:'invalid'}} 400 shape rather
 * than a generic validation-error response).
 *
 * Global Jackson naming is SNAKE_CASE (application.yml), so plain
 * camelCase Java field names already bind to bookingId/reference/eventId
 * -> booking_id/reference/event_id without any @JsonNaming override —
 * unlike the payment DTOs (Phase D), this request's wire format happens to
 * already be snake_case in the existing route.
 *
 * eventId is accepted but INTENTIONALLY NEVER READ anywhere in this
 * codebase — mirrors the existing route exactly: the QR's own claimed
 * event_id is parsed but never used past validation; the booking's actual
 * event_id (read from the database) is what CheckInService/
 * BookingRepository#checkIn treats as authoritative. See the Phase F
 * report for why this matters (wrong-event protection).
 */
public record CheckInRequest(
    UUID bookingId,
    String reference,
    String eventId
) {
}
