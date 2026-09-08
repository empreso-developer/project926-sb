package com.project926.backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Mirrors lib/validations/event.ts TicketTypeSchema exactly (name 2-80,
 * price >= 0, quantity_total 1-100000, sale_start/sale_end optional). No
 * eventId (comes from the path), no id, and critically no quantity_sold —
 * the existing createTicketTypeAction never sets it either; it is left at
 * its database default of 0 (see supabase/schema.sql).
 */
public record CreateTicketTypeRequest(
    @NotBlank @Size(min = 2, max = 80) String name,
    @NotNull @DecimalMin("0") BigDecimal price,
    @NotNull @Min(1) @Max(100000) Integer quantityTotal,
    OffsetDateTime saleStart,
    OffsetDateTime saleEnd
) {
}
