package com.project926.backend.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.UUID;

/**
 * Mirrors the existing create-order response exactly:
 * {@code { orderId, bookingId, amount, currency, keyId }} — same field
 * names, same camelCase contract (see CreateOrderRequest's Javadoc for why
 * @JsonNaming overrides the global snake_case default here). {@code amount}
 * is in paise (already multiplied by 100), matching the existing
 * {@code Math.round(totalAmount * 100)}.
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record CreateOrderResponse(
    String orderId,
    UUID bookingId,
    long amount,
    String currency,
    String keyId
) {
}
