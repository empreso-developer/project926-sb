package com.project926.backend.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Consistent error shape across the API. Never leaks exception messages,
 * SQL, or stack traces to the client (Step 9) — those go to the server log
 * only; the client gets a fixed, generic message per status code.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({EventNotFoundException.class, TicketTypeNotFoundException.class})
    public ResponseEntity<Map<String, Object>> handleNotFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body("Not found"));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(ForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body("Forbidden"));
    }

    /**
     * Payment-flow-specific 404s ("Event not found", "Booking not found") —
     * unlike handleNotFound() above, the exact existing message text is
     * preserved (see PaymentFlowNotFoundException's Javadoc).
     */
    @ExceptionHandler(PaymentFlowNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handlePaymentFlowNotFound(PaymentFlowNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(ex.getMessage()));
    }

    /**
     * Payment-flow-specific 400s with preserved literal message text — see
     * BookingValidationException's Javadoc.
     */
    @ExceptionHandler(BookingValidationException.class)
    public ResponseEntity<Map<String, Object>> handleBookingValidation(BookingValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body(ex.getMessage()));
    }

    /** Mirrors the verify route's SOLD_OUT branch — see SoldOutException's Javadoc. */
    @ExceptionHandler(SoldOutException.class)
    public ResponseEntity<Map<String, Object>> handleSoldOut(SoldOutException ex) {
        log.error("SOLD_OUT after captured payment — manual refund required: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body(
            "This ticket type sold out while confirming your payment. Your payment was captured — our team will contact you to refund it."
        ));
    }

    /** Mirrors the verify route's generic confirmErr branch — see BookingConfirmationException's Javadoc. */
    @ExceptionHandler(BookingConfirmationException.class)
    public ResponseEntity<Map<String, Object>> handleBookingConfirmation(BookingConfirmationException ex) {
        log.error("Failed to confirm booking: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body("Failed to confirm booking"));
    }

    /** Mirrors the verify route's `catch (fetchErr)` around razorpay.payments.fetch — see RazorpayGatewayException's Javadoc. */
    @ExceptionHandler(RazorpayGatewayException.class)
    public ResponseEntity<Map<String, Object>> handleRazorpayGateway(RazorpayGatewayException ex) {
        log.error("Razorpay gateway error", ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body("Could not verify payment with Razorpay"));
    }

    /**
     * Ticket-type deletion is protected by the existing
     * booking_items.ticket_type_id FK (ON DELETE RESTRICT — see
     * supabase/schema.sql): deleting a ticket type that has booking_items
     * against it fails at the database level. That failure surfaces here as
     * a DataIntegrityViolationException; mapped to 409, matching Step 13's
     * "409 where the existing behavior represents a conflict" — the
     * existing Next.js code has no explicit handling for this either, it
     * just lets the Postgres error propagate, so this is the same outcome
     * with a proper HTTP status instead of an unhandled 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body("Conflict"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body("Invalid request"));
    }

    /**
     * Malformed JSON (unparseable, wrong type for a field, e.g. a
     * non-UUID string for a UUID field) — this is a request-parsing
     * failure distinct from bean-validation (MethodArgumentNotValidException
     * above), and previously fell through to the generic 500 handler
     * uncaught. Found and fixed during Phase D testing; general request-
     * parsing hygiene, not a change to any business logic.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body("Invalid request"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleBadPathVariable(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body("Invalid request"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body("Internal server error"));
    }

    private Map<String, Object> body(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        return body;
    }
}
