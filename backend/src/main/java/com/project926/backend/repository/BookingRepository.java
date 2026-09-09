package com.project926.backend.repository;

import com.project926.backend.entity.Booking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
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

    /** Mirrors the check-in route's manual-fallback lookup: {@code .eq('reference', reference)}. */
    Optional<Booking> findByReference(String reference);

    /**
     * Mirrors the attendees page's stats query source rows: all confirmed
     * bookings for the event (used to compute totalSold/checkedInQty from
     * their booking_items — see AttendeeService).
     */
    List<Booking> findByEventIdAndStatus(UUID eventId, String status);

    /**
     * THE atomic, guarded check-in — mirrors the existing route's exact
     * statement:
     * {@code .update({checked_in_at, checked_in_by}).eq('id',bookingId).eq('event_id',eventId).eq('status','confirmed').is('checked_in_at',null)}
     * — a single UPDATE...WHERE, not a read-then-write. The event
     * relationship is part of THIS guarded statement (not a separate Java
     * check beforehand), so a booking for a different event can never be
     * checked in via this call regardless of what the caller believes the
     * booking's event to be — the WHERE clause is the actual authority.
     * Two concurrent calls for the same booking serialize on Postgres's row
     * lock; the second to commit re-evaluates {@code checked_in_at IS NULL}
     * against the now-committed row and updates zero rows. Returns rows
     * affected (0 or 1) so the caller can tell whether IT won the race.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Booking b SET b.checkedInAt = :checkedInAt, b.checkedInBy = :checkedInBy " +
        "WHERE b.id = :bookingId AND b.eventId = :eventId AND b.status = 'confirmed' AND b.checkedInAt IS NULL")
    int checkIn(
        @Param("bookingId") UUID bookingId,
        @Param("eventId") UUID eventId,
        @Param("checkedInAt") OffsetDateTime checkedInAt,
        @Param("checkedInBy") String checkedInBy
    );

    /**
     * Mirrors the attendees page's main paginated query exactly:
     * {@code .eq('event_id',eventId).eq('status','confirmed')}, optional
     * checked-in filter, optional search (booking reference OR customer
     * name/email — via an EXISTS subquery against profiles rather than a
     * JPA relationship, consistent with this project's existing style of
     * not adding entity associations purely for one query), ordered by
     * created_at descending, paginated.
     *
     * {@code filter} is one of "all"/"checked_in"/"not_checked_in";
     * {@code searchPattern} is either "" (no search — matches the existing
     * `if (search)` branch being skipped entirely) or a lowercase
     * "%term%" LIKE pattern.
     */
    @Query(
        value = "SELECT b FROM Booking b WHERE b.eventId = :eventId AND b.status = 'confirmed' " +
            "AND (:filter = 'all' OR (:filter = 'checked_in' AND b.checkedInAt IS NOT NULL) OR (:filter = 'not_checked_in' AND b.checkedInAt IS NULL)) " +
            "AND (:searchPattern = '' OR LOWER(b.reference) LIKE :searchPattern ESCAPE '\\' " +
            "     OR EXISTS (SELECT 1 FROM Profile p WHERE p.id = b.customerId AND " +
            "                (LOWER(COALESCE(p.firstName, '')) LIKE :searchPattern ESCAPE '\\' OR LOWER(COALESCE(p.lastName, '')) LIKE :searchPattern ESCAPE '\\' OR LOWER(p.email) LIKE :searchPattern ESCAPE '\\'))) " +
            "ORDER BY b.createdAt DESC",
        countQuery = "SELECT COUNT(b) FROM Booking b WHERE b.eventId = :eventId AND b.status = 'confirmed' " +
            "AND (:filter = 'all' OR (:filter = 'checked_in' AND b.checkedInAt IS NOT NULL) OR (:filter = 'not_checked_in' AND b.checkedInAt IS NULL)) " +
            "AND (:searchPattern = '' OR LOWER(b.reference) LIKE :searchPattern ESCAPE '\\' " +
            "     OR EXISTS (SELECT 1 FROM Profile p WHERE p.id = b.customerId AND " +
            "                (LOWER(COALESCE(p.firstName, '')) LIKE :searchPattern ESCAPE '\\' OR LOWER(COALESCE(p.lastName, '')) LIKE :searchPattern ESCAPE '\\' OR LOWER(p.email) LIKE :searchPattern ESCAPE '\\')))"
    )
    Page<Booking> findAttendees(
        @Param("eventId") UUID eventId,
        @Param("filter") String filter,
        @Param("searchPattern") String searchPattern,
        Pageable pageable
    );
}
