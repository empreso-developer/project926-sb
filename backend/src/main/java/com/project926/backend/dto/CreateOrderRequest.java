package com.project926.backend.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Mirrors the existing create-order route's zod {@code Body} schema
 * exactly: {@code { eventId: uuid, items: [{ ticketTypeId: uuid, quantity: 1-10 }], 1-10 items }}.
 *
 * IMPORTANT: this endpoint's JSON contract is the existing hand-written
 * camelCase shape the frontend already sends (booking-widget.tsx), NOT the
 * snake_case convention used elsewhere in this API for Supabase-shaped
 * DTOs. spring.jackson.property-naming-strategy is SNAKE_CASE globally
 * (application.yml) — @JsonNaming here opts this DTO back out to plain
 * camelCase so field names stay eventId/ticketTypeId/quantity exactly as
 * the existing frontend sends them, not event_id/ticket_type_id.
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record CreateOrderRequest(
    @NotNull UUID eventId,
    @NotNull @Size(min = 1, max = 10) @Valid List<Item> items
) {
    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    public record Item(
        @NotNull UUID ticketTypeId,
        @NotNull @Min(1) @Max(10) Integer quantity
    ) {
    }
}
