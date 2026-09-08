package com.project926.backend.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Mirrors the existing TicketType shape (lib/types.ts). Field names are
 * camelCase in Java and serialized as snake_case JSON (see application.yml
 * spring.jackson.property-naming-strategy) to match the existing
 * quantity_total/quantity_sold/etc. field names the frontend already
 * expects, ahead of a future frontend cutover.
 *
 * Raw quantities only, exactly as the existing Supabase query returns them
 * — no derived "available" field, since the existing Next.js implementation
 * computes availability client-side (quantity_total - quantity_sold), not
 * in the query.
 */
public record TicketTypeDto(
    UUID id,
    UUID eventId,
    String name,
    BigDecimal price,
    Integer quantityTotal,
    Integer quantitySold,
    OffsetDateTime saleStart,
    OffsetDateTime saleEnd,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
