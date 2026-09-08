package com.project926.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Maps to the existing {@code payments} table (see supabase/schema.sql).
 * Created once at order-creation time (status "created"), then updated at
 * verification time — see PaymentRepository's @Modifying methods for the
 * exact update statements this reproduces.
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "booking_id", nullable = false, updatable = false)
    private UUID bookingId;

    @Column(name = "razorpay_order_id")
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id")
    private String razorpayPaymentId;

    @Column(name = "razorpay_signature")
    private String razorpaySignature;

    @Column(name = "amount", nullable = false, updatable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, updatable = false)
    private String currency;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Payment() {
        // JPA
    }

    /**
     * Mirrors the existing insert exactly:
     * {@code .from('payments').insert({ booking_id, razorpay_order_id, amount, currency, status: 'created' })}
     * — razorpay_payment_id/razorpay_signature are left null until
     * verification, status starts at "created".
     */
    public static Payment createForOrder(UUID bookingId, String razorpayOrderId, BigDecimal amount, String currency) {
        Payment p = new Payment();
        p.id = UUID.randomUUID();
        p.bookingId = bookingId;
        p.razorpayOrderId = razorpayOrderId;
        p.amount = amount;
        p.currency = currency;
        p.status = "created";
        OffsetDateTime now = OffsetDateTime.now();
        p.createdAt = now;
        p.updatedAt = now;
        return p;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public String getRazorpayOrderId() {
        return razorpayOrderId;
    }

    public String getRazorpayPaymentId() {
        return razorpayPaymentId;
    }

    public String getRazorpaySignature() {
        return razorpaySignature;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
