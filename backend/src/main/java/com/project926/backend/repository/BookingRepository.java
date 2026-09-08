package com.project926.backend.repository;

import com.project926.backend.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    /**
     * Mirrors the verify route's defensive hold-extension exactly:
     * {@code .update({ expires_at }).eq('id', bookingId).eq('status', 'pending')}
     * — a single conditional UPDATE, not a read-then-write, so it can never
     * race with a concurrent confirmation the way a load+save would.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Booking b SET b.expiresAt = :expiresAt WHERE b.id = :id AND b.status = 'pending'")
    void extendExpiresAtIfPending(@Param("id") UUID id, @Param("expiresAt") OffsetDateTime expiresAt);

    /**
     * Mirrors the plain {@code .update({ status }).eq('id', bookingId)}
     * calls used for the reject/cancel and SOLD_OUT paths — unconditional
     * on current status, exactly like the existing calls.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Booking b SET b.status = :status WHERE b.id = :id")
    void updateStatus(@Param("id") UUID id, @Param("status") String status);

    /**
     * Mirrors sendBookingConfirmationEmailOnce's atomic claim exactly:
     * {@code .update({ ticket_email_sent_at }).eq('id', booking.id).is('ticket_email_sent_at', null)}
     * — a single conditional UPDATE, not read-then-write, so two concurrent
     * or retried verification requests for the same booking can never both
     * win the claim. Returns the number of rows updated (0 or 1) so the
     * caller can tell whether IT won the claim, exactly like the existing
     * code checking whether {@code claim.data} came back non-null.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Booking b SET b.ticketEmailSentAt = :sentAt WHERE b.id = :id AND b.ticketEmailSentAt IS NULL")
    int claimTicketEmailSend(@Param("id") UUID id, @Param("sentAt") OffsetDateTime sentAt);

    /**
     * Mirrors the catch block's
     * {@code .update({ ticket_email_error: message.slice(0, 500) }).eq('id', booking.id)}.
     * Truncation to 500 chars happens in the caller (TicketEmailService),
     * matching the existing {@code .slice(0, 500)} call site exactly.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Booking b SET b.ticketEmailError = :error WHERE b.id = :id")
    void recordTicketEmailError(@Param("id") UUID id, @Param("error") String error);
}
