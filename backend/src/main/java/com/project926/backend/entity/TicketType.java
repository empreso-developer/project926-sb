package com.project926.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Maps to the existing {@code ticket_types} table (see
 * supabase/schema.sql). Read-only in Phase B — inventory mutation
 * (quantity_sold) happens only via the existing
 * {@code create_booking_from_items} / {@code confirm_booking_and_commit_inventory}
 * RPCs, not touched here.
 */
@Entity
@Table(name = "ticket_types")
public class TicketType {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    @Column(name = "quantity_total", nullable = false)
    private Integer quantityTotal;

    @Column(name = "quantity_sold", nullable = false)
    private Integer quantitySold;

    @Column(name = "sale_start")
    private OffsetDateTime saleStart;

    @Column(name = "sale_end")
    private OffsetDateTime saleEnd;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected TicketType() {
        // JPA
    }

    /**
     * Mirrors createTicketTypeAction's insert exactly: quantity_sold is
     * never set here — it defaults to 0 at the database level (see
     * supabase/schema.sql), exactly as the existing insert leaves it
     * untouched. There is no ticket-type update action in the existing
     * application, so this entity intentionally has no setters at all.
     */
    public static TicketType create(
        UUID eventId,
        String name,
        BigDecimal price,
        Integer quantityTotal,
        OffsetDateTime saleStart,
        OffsetDateTime saleEnd
    ) {
        TicketType t = new TicketType();
        t.id = UUID.randomUUID();
        t.eventId = eventId;
        t.name = name;
        t.price = price;
        t.quantityTotal = quantityTotal;
        t.quantitySold = 0;
        t.saleStart = saleStart;
        t.saleEnd = saleEnd;
        OffsetDateTime now = OffsetDateTime.now();
        t.createdAt = now;
        t.updatedAt = now;
        return t;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Integer getQuantityTotal() {
        return quantityTotal;
    }

    public Integer getQuantitySold() {
        return quantitySold;
    }

    public OffsetDateTime getSaleStart() {
        return saleStart;
    }

    public OffsetDateTime getSaleEnd() {
        return saleEnd;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
