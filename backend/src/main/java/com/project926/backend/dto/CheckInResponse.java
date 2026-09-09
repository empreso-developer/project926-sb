package com.project926.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Mirrors the existing check-in route's response shape EXACTLY — including
 * its unusual HTTP-status convention: almost every business outcome here
 * (invalid/wrong_event/payment_not_confirmed/cancelled/already_checked_in/
 * checked_in) returns HTTP 200, with the outcome distinguished only by the
 * {@code status} field. Only auth failure (401/404/403) and malformed body
 * (400) get non-200 codes — see CheckInController. Confirmed against the
 * actual source before implementing (Step 1), not assumed.
 *
 * Field casing is intentionally MIXED, matching the existing object
 * literal exactly: {@code checkedInAt} needs no override (the global
 * SNAKE_CASE strategy already turns it into {@code checked_in_at}, which
 * is what the existing route's raw {@code checked_in_at} field name is),
 * but {@code ticketTypes} DOES need an explicit @JsonProperty override —
 * without it, the global strategy would turn it into {@code ticket_types},
 * which is NOT what the existing {@code AttendeeInfo.ticketTypes} field is
 * called on the wire.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CheckInResponse(
    boolean success,
    String status,
    AttendeeInfo attendee,
    BookingRef booking,
    OffsetDateTime checkedInAt,
    String error
) {

    public record AttendeeInfo(
        String name,
        String email,
        @JsonProperty("ticketTypes") List<TicketCountDto> ticketTypes
    ) {
    }

    public record BookingRef(String reference) {
    }

    public static CheckInResponse status(String status) {
        return new CheckInResponse(false, status, null, null, null, null);
    }

    public static CheckInResponse unauthorized(String errorMessage) {
        return new CheckInResponse(false, "unauthorized", null, null, null, errorMessage);
    }

    public static CheckInResponse alreadyCheckedIn(AttendeeInfo attendee, String reference, OffsetDateTime checkedInAt) {
        return new CheckInResponse(false, "already_checked_in", attendee, new BookingRef(reference), checkedInAt, null);
    }

    public static CheckInResponse checkedIn(AttendeeInfo attendee, String reference, OffsetDateTime checkedInAt) {
        return new CheckInResponse(true, "checked_in", attendee, new BookingRef(reference), checkedInAt, null);
    }
}
