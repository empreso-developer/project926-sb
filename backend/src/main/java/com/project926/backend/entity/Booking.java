package com.project926.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Maps to the existing {@code bookings} table (see supabase/schema.sql).
 * checked_in_at/checked_in_by (Phase F) and ticket_email_sent_at/
 * ticket_email_error (Phase E) are now all mapped.
 *
 * No entity mutation happens for status/total_amount/qr_code/checked_in_at/
 * checked_in_by — those are exclusively written either by the existing
 * create_booking_from_items/confirm_booking_and_commit_inventory RPCs (see
 * BookingInventoryRepository) or by explicit BookingRepository
 * @Modifying queries (one UPDATE statement per existing
 * `.update().eq()`/guarded-UPDATE call, not entity dirty-checking) — never
 * by mutating a loaded Booking object and calling save(). This entity has
 * no setters for exactly that reason.
 */
@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "reference", nullable = false, updatable = false)
    private String reference;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private String customerId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "qr_code")
    private String qrCode;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "ticket_email_sent_at")
    private OffsetDateTime ticketEmailSentAt;

    @Column(name = "ticket_email_error")
    private String ticketEmailError;

    @Column(name = "checked_in_at")
    private OffsetDateTime checkedInAt;

    /** Clerk id of the organizer/admin who checked this booking in — text, references profiles(id), same reasoning as customerId. */
    @Column(name = "checked_in_by")
    private String checkedInBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Booking() {
        // JPA
    }

    public UUID getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public String getCustomerId() {
        return customerId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getQrCode() {
        return qrCode;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getTicketEmailSentAt() {
        return ticketEmailSentAt;
    }

    public String getTicketEmailError() {
        return ticketEmailError;
    }

    public OffsetDateTime getCheckedInAt() {
        return checkedInAt;
    }

    public String getCheckedInBy() {
        return checkedInBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
