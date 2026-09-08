package com.project926.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Maps to the existing {@code booking_items} table (see
 * supabase/schema.sql). Read-only in Phase E — rows are created exclusively
 * by the existing create_booking_from_items RPC (Phase D), never by Java.
 * Needed here only to build the ticket confirmation email's line items,
 * mirroring send-ticket-confirmation.ts's
 * {@code booking_items(quantity, unit_price, subtotal, ticket_type:ticket_types(name))} select.
 */
@Entity
@Table(name = "booking_items")
public class BookingItem {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "booking_id", nullable = false, updatable = false)
    private UUID bookingId;

    @Column(name = "ticket_type_id", nullable = false, updatable = false)
    private UUID ticketTypeId;

    @Column(name = "quantity", nullable = false, updatable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, updatable = false)
    private BigDecimal unitPrice;

    @Column(name = "subtotal", nullable = false, updatable = false)
    private BigDecimal subtotal;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected BookingItem() {
        // JPA
    }

    public UUID getId() {
        return id;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public UUID getTicketTypeId() {
        return ticketTypeId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
