package com.project926.backend.service.email;

import java.math.BigDecimal;
import java.util.List;

/**
 * Java equivalent of lib/email/types.ts's TicketConfirmationEmailData.
 * Purely internal — never serialized as an HTTP response, so it is NOT
 * subject to the global Jackson SNAKE_CASE naming strategy that governs
 * REST DTOs elsewhere in this API.
 */
public record TicketConfirmationEmailData(
    Customer customer,
    BookingInfo booking,
    EventInfo event,
    List<TicketLine> tickets,
    PaymentInfo payment,
    /** The exact data URL already stored in bookings.qr_code — never regenerated. */
    String qrCodeDataUrl,
    String dashboardUrl
) {
    public record Customer(String firstName, String lastName, String email) {
    }

    public record BookingInfo(String id, String reference, BigDecimal totalAmount) {
    }

    public record EventInfo(String title, String date, String time, String venue, String city, String bannerUrl) {
    }

    public record TicketLine(String name, int quantity, BigDecimal unitPrice, BigDecimal subtotal) {
    }

    public record PaymentInfo(BigDecimal amount, String currency, String paymentId) {
    }
}
