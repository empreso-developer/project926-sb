package com.project926.backend.repository;

import com.project926.backend.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /**
     * Mirrors {@code .eq('booking_id', bookingId).order('created_at', {ascending:false}).limit(1)}.
     * Spring Data derives the LIMIT 1 from the "First" keyword.
     */
    Optional<Payment> findFirstByBookingIdOrderByCreatedAtDesc(UUID bookingId);

    /** Mirrors the rejectPayment() helper's {@code .update({ status: 'failed' }).eq('id', payment.id)}. */
    @Modifying
    @Transactional
    @Query("UPDATE Payment p SET p.status = :status WHERE p.id = :id")
    void updateStatus(@Param("id") UUID id, @Param("status") String status);

    /**
     * Mirrors the "mark paid" update (also reused verbatim for the
     * SOLD_OUT branch, which sets the identical three fields):
     * {@code .update({ razorpay_payment_id, razorpay_signature, status }).eq('id', payment.id)}.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Payment p SET p.razorpayPaymentId = :razorpayPaymentId, p.razorpaySignature = :razorpaySignature, p.status = :status WHERE p.id = :id")
    void markPaid(
        @Param("id") UUID id,
        @Param("razorpayPaymentId") String razorpayPaymentId,
        @Param("razorpaySignature") String razorpaySignature,
        @Param("status") String status
    );
}
