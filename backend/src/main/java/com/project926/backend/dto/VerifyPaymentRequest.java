package com.project926.backend.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Mirrors the existing verify route's zod {@code Body} schema exactly:
 * {@code { razorpayOrderId, razorpayPaymentId, razorpaySignature, bookingId }}.
 * See CreateOrderRequest's Javadoc for why @JsonNaming overrides the global
 * snake_case default for this endpoint.
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record VerifyPaymentRequest(
    @NotBlank String razorpayOrderId,
    @NotBlank String razorpayPaymentId,
    @NotBlank String razorpaySignature,
    @NotNull UUID bookingId
) {
}
