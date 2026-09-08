package com.project926.backend.repository;

import com.project926.backend.exception.BookingConfirmationException;
import com.project926.backend.exception.BookingValidationException;
import com.project926.backend.exception.SoldOutException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Calls the two existing, unmodified Postgres functions
 * (create_booking_from_items, confirm_booking_and_commit_inventory — see
 * supabase/schema.sql) directly via JDBC. This class deliberately contains
 * NO inventory logic of its own: no read-check-write, no row locking, no
 * hold-expiry calculation. Every guarantee (row locking in ascending
 * ticket_type_id order, the 15-minute pending hold, the active-holds
 * calculation, idempotent confirmation, SOLD_OUT detection, overselling
 * prevention) lives entirely inside the SQL functions and is exercised
 * here exactly as the existing Next.js code exercises it via
 * supabaseAdmin.rpc(...) — same function names, same parameter names/
 * order/types, same return types.
 */
@Repository
public class BookingInventoryRepository {

    private final JdbcTemplate jdbcTemplate;

    public BookingInventoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * {@code create_booking_from_items(p_customer_id text, p_event_id uuid, p_items jsonb) RETURNS uuid}
     * — exact signature per supabase/schema.sql. {@code itemsJson} must be a
     * JSON array of {@code {"ticket_type_id": "...", "quantity": n}}
     * objects, matching the existing itemsPayload shape exactly.
     *
     * @throws BookingValidationException with the RPC's own RAISE EXCEPTION
     *     message (e.g. "Ticket type not found", "Not enough tickets
     *     available for General") — reproducing the existing route's
     *     {@code {error: bookingErr.message}} / 400 behavior.
     */
    public UUID createBookingFromItems(String customerId, UUID eventId, String itemsJson) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT create_booking_from_items(?, ?, ?::jsonb)",
                UUID.class,
                customerId, eventId, itemsJson
            );
        } catch (DataAccessException e) {
            throw new BookingValidationException(extractPostgresMessage(e));
        }
    }

    /**
     * {@code confirm_booking_and_commit_inventory(p_booking_id uuid, p_qr_code text) RETURNS boolean}
     * — exact signature per supabase/schema.sql. Returns {@code true} if
     * this call newly confirmed the booking, {@code false} if it was
     * already confirmed (idempotent no-op — the function's own guarantee,
     * not reproduced here).
     *
     * @throws SoldOutException when the RPC raises its SOLD_OUT-prefixed
     *     exception (inventory ran out between hold and confirmation).
     * @throws BookingValidationException for any other RPC-raised error
     *     (e.g. "Booking not found", "Booking is not pending").
     */
    public boolean confirmBookingAndCommitInventory(UUID bookingId, String qrCode) {
        try {
            Boolean result = jdbcTemplate.queryForObject(
                "SELECT confirm_booking_and_commit_inventory(?, ?)",
                Boolean.class,
                bookingId, qrCode
            );
            return Boolean.TRUE.equals(result);
        } catch (DataAccessException e) {
            String message = extractPostgresMessage(e);
            if (message != null && message.startsWith("SOLD_OUT")) {
                throw new SoldOutException(message);
            }
            // Any other confirm_booking_and_commit_inventory failure is a
            // generic 500 with a fixed message client-side — see
            // BookingConfirmationException's Javadoc for why this differs
            // from createBookingFromItems's message-preserving behavior.
            throw new BookingConfirmationException(message);
        }
    }

    /**
     * Postgres RAISE EXCEPTION text arrives via the JDBC driver prefixed
     * with "ERROR: " and can carry additional DETAIL/HINT/CONTEXT lines
     * appended after a newline; Supabase's PostgREST/rpc layer (what the
     * existing Next.js code sees as {@code error.message}) surfaces just
     * the clean primary message. This strips both, so the client-facing
     * text matches what the existing route would show.
     */
    private static String extractPostgresMessage(DataAccessException e) {
        Throwable cause = e.getMostSpecificCause();
        String raw = cause != null ? cause.getMessage() : e.getMessage();
        if (raw == null) {
            return "Database error";
        }
        String firstLine = raw.split("\\r?\\n", 2)[0].trim();
        if (firstLine.startsWith("ERROR: ")) {
            firstLine = firstLine.substring("ERROR: ".length());
        }
        return firstLine;
    }
}
