package com.project926.backend.service;

import com.project926.backend.dto.RazorpayPaymentEntity;
import com.project926.backend.entity.Booking;
import com.project926.backend.entity.Payment;
import com.project926.backend.exception.SoldOutException;
import com.project926.backend.repository.BookingRepository;
import com.project926.backend.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.Set;

/**
 * The Razorpay webhook (Phase G): a SECONDARY confirmation path for the
 * exact same payment/booking state machine PaymentService.verifyPayment
 * already implements (Phase D) — not a second, independent implementation.
 * See PaymentService.confirmCapturedPayment, which this class calls for
 * the actual confirm-inventory/mark-paid/send-email sequence.
 *
 * <h2>Event selection</h2>
 * No prior webhook implementation or event-selection decision existed
 * anywhere in the repository (confirmed by audit — see the Phase G
 * report). The minimum event set chosen to safely complement the existing
 * browser-driven /verify flow:
 * <ul>
 *   <li>{@code payment.captured} — lets a booking be confirmed even if the
 *       customer's browser never completes its own {@code /verify} call
 *       (tab closed, network failure, etc.) — this is the webhook's entire
 *       reason for existing.</li>
 *   <li>{@code payment.failed} — lets the payment row reflect a failure
 *       promptly instead of sitting at {@code "created"} until the
 *       booking's 15-minute hold silently expires; deliberately does NOT
 *       cancel the booking (see {@link #handleFailed}).</li>
 * </ul>
 * {@code order.paid} is deliberately NOT handled: it fires for the order
 * as a whole and carries no information beyond what {@code payment.captured}
 * already provides for this app's one-payment-per-order model, so handling
 * it too would only add a second, redundant path to the same confirmation
 * logic — an unnecessary event to support, not a missing behavior of the
 * existing app.
 *
 * <h2>Correlation strategy</h2>
 * The existing create-order implementation (PaymentService.createOrder)
 * stores the Razorpay order id on the {@code payments} row
 * (razorpay_order_id) at order-creation time, and separately attaches
 * {@code notes.booking_id}/{@code receipt} to the Razorpay order itself.
 * The webhook payload's {@code payment.entity.order_id} is the most
 * direct, already-established correlation key back to that row — see
 * {@link com.project926.backend.repository.PaymentRepository#findByRazorpayOrderId}.
 * The Razorpay order's own {@code notes}/{@code receipt} fields are not
 * used (the webhook payload does not even carry them — those live on the
 * order object, not the payment object this webhook receives), so no new
 * correlation mechanism was invented here; the existing one is reused.
 *
 * <h2>Idempotency</h2>
 * No new webhook-event-id/delivery-id table was added — the existing
 * {@code payments.status} and {@code bookings.status} columns are already
 * sufficient (Section 10 of the Phase G brief: don't add a table unless
 * the existing schema is proven insufficient, and it isn't). A duplicate
 * {@code payment.captured} delivery for an already-confirmed booking short-
 * circuits at the same idempotent checks verifyPayment itself relies on:
 * the RPC's own {@code IF v_status = 'confirmed' THEN RETURN false} guard
 * (never double-increments quantity_sold), markPaid is a plain idempotent
 * UPDATE, and the ticket email has its own atomic claim
 * (BookingRepository.claimTicketEmailSend). This class's own
 * booking.getStatus()=="confirmed" check before ever calling
 * confirmCapturedPayment is a cheap short-circuit on top of those
 * guarantees, not a replacement for them — see
 * PaymentService.confirmCapturedPayment's Javadoc for why the RPC-level
 * guarantee is what actually matters under concurrent delivery.
 */
@Service
public class RazorpayWebhookService {

    private static final Logger log = LoggerFactory.getLogger(RazorpayWebhookService.class);
    private static final Set<String> SUPPORTED_EVENTS = Set.of("payment.captured", "payment.failed");

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final PaymentService paymentService;

    public RazorpayWebhookService(
        PaymentRepository paymentRepository,
        BookingRepository bookingRepository,
        PaymentService paymentService
    ) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.paymentService = paymentService;
    }

    /**
     * Processes one already-signature-verified, already-parsed webhook
     * event. Every outcome that represents a fully-handled, non-actionable
     * business state (unknown event, no matching payment/booking, stale
     * booking state, amount/currency mismatch, SOLD_OUT) completes
     * normally — the controller acks these with HTTP 200, since retrying
     * would produce the identical outcome. Only
     * {@link com.project926.backend.exception.BookingConfirmationException}
     * (an unexpected RPC failure — genuinely worth a retry) is allowed to
     * propagate.
     */
    public void process(String eventType, RazorpayPaymentEntity paymentEntity) {
        if (!SUPPORTED_EVENTS.contains(eventType)) {
            log.info("[webhook] Ignoring unsupported/unhandled event type: {}", eventType);
            return;
        }
        if (paymentEntity == null || paymentEntity.orderId() == null) {
            log.warn("[webhook] Ignoring {} event with no payment.entity.order_id", eventType);
            return;
        }

        Optional<Payment> paymentOpt = paymentRepository.findByRazorpayOrderId(paymentEntity.orderId());
        if (paymentOpt.isEmpty()) {
            // Never happens for orders this app created (createOrder always
            // inserts a payment row alongside the Razorpay order), but a
            // webhook is untrusted-origin input by nature — handle safely
            // rather than assuming the correlation always succeeds.
            log.warn("[webhook] No local payment record for razorpay order {} (event {})", paymentEntity.orderId(), eventType);
            return;
        }
        Payment payment = paymentOpt.get();

        Booking booking = bookingRepository.findById(payment.getBookingId()).orElse(null);
        if (booking == null) {
            log.error("[webhook] Payment {} references missing booking {} (event {})", payment.getId(), payment.getBookingId(), eventType);
            return;
        }

        if ("payment.captured".equals(eventType)) {
            handleCaptured(payment, booking, paymentEntity);
        } else {
            handleFailed(payment, booking, paymentEntity);
        }
    }

    /**
     * Mirrors PaymentService.verifyPayment's own captured-payment
     * validations, adapted to trusted webhook data: verify() calls
     * razorpay.payments.fetch() to independently confirm status/amount/
     * currency because the browser-supplied ids are untrusted; a webhook
     * delivery is ALREADY an independent, HMAC-signed assertion straight
     * from Razorpay (that's what the signature proves), so re-fetching the
     * same payment from Razorpay's API would only add a redundant network
     * call with no additional trust — see the Phase G report's "Razorpay
     * API calls" section. The one thing still worth cross-checking locally
     * is amount/currency against what THIS app expected when it created
     * the order (Section 21 of the brief): a valid Razorpay signature
     * proves the payload came from Razorpay, not that it's the payload we
     * expected for this specific order.
     */
    private void handleCaptured(Payment payment, Booking booking, RazorpayPaymentEntity paymentEntity) {
        if (!"captured".equals(paymentEntity.status())) {
            log.warn("[webhook] payment.captured event for order {} carried inconsistent status '{}' — ignoring",
                paymentEntity.orderId(), paymentEntity.status());
            return;
        }

        long expectedPaise = payment.getAmount()
            .multiply(BigDecimal.valueOf(100))
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact();
        if (paymentEntity.amount() == null || paymentEntity.amount() != expectedPaise
            || !payment.getCurrency().equals(paymentEntity.currency())) {
            log.error("[webhook] Amount/currency mismatch for order {}: expected {} {}, got {} {} — not confirming",
                paymentEntity.orderId(), expectedPaise, payment.getCurrency(), paymentEntity.amount(), paymentEntity.currency());
            return;
        }

        if ("confirmed".equals(booking.getStatus())) {
            log.info("[webhook] Booking {} already confirmed — duplicate payment.captured delivery, no-op", booking.getReference());
            return;
        }
        if (!"pending".equals(booking.getStatus())) {
            // The booking is cancelled (e.g. an earlier SOLD_OUT confirmation
            // failure, or a rejected verify attempt) but Razorpay genuinely
            // captured the money — the same "captured but unfulfillable"
            // situation Section 20/SOLD_OUT already establishes precedent
            // for: record it for manual reconciliation, never resurrect the
            // booking, never auto-refund. Skipped entirely if this specific
            // payment was already marked paid (idempotent — e.g. this is a
            // duplicate delivery of an event already handled this way, or by
            // the SOLD_OUT path in confirmCapturedPayment itself).
            if (!"paid".equals(payment.getStatus())) {
                log.error(
                    "[webhook] CRITICAL: order {} payment {} captured but booking {} is '{}' — manual refund required",
                    paymentEntity.orderId(), paymentEntity.id(), booking.getReference(), booking.getStatus()
                );
                paymentRepository.markPaid(payment.getId(), paymentEntity.id(), null, "paid");
            }
            return;
        }

        try {
            paymentService.confirmCapturedPayment(booking, payment, paymentEntity.id(), null);
        } catch (SoldOutException soldOut) {
            // Fully handled inside confirmCapturedPayment (payment marked
            // paid, booking cancelled, CRITICAL logged) — a genuine terminal
            // business outcome, not a failure worth retrying.
            log.warn("[webhook] SOLD_OUT confirming booking {} from webhook: {}", booking.getReference(), soldOut.getMessage());
        }
        // BookingConfirmationException intentionally propagates uncaught —
        // an unexpected RPC failure is exactly the kind of transient
        // condition worth letting Razorpay's webhook retry mechanism retry.
    }

    /**
     * Deliberately does NOT reproduce PaymentService.rejectPayment's
     * booking cancellation. rejectPayment() runs inside an active,
     * synchronous /verify call that has already investigated a SPECIFIC
     * reason a signature/amount/status check failed; a bare
     * payment.failed webhook alone carries much less context and can
     * arrive for reasons that don't warrant discarding the booking (e.g.
     * a transient card decline the customer immediately retries against a
     * NEW order — this app always creates a fresh booking+order per
     * checkout attempt, so this booking's 15-minute hold is the existing,
     * already-tested mechanism that reclaims its inventory; no new
     * cancellation/refund system is introduced here per Section 18 of the
     * brief).
     */
    private void handleFailed(Payment payment, Booking booking, RazorpayPaymentEntity paymentEntity) {
        if ("paid".equals(payment.getStatus())) {
            // Out-of-order delivery (Section 19, Case D): a payment.failed
            // event must never overwrite an already-successful payment.
            log.info("[webhook] Ignoring payment.failed for order {} — payment already paid", paymentEntity.orderId());
            return;
        }
        if ("failed".equals(payment.getStatus())) {
            log.info("[webhook] Payment for order {} already marked failed — duplicate delivery, no-op", paymentEntity.orderId());
            return;
        }

        paymentRepository.updateStatus(payment.getId(), "failed");
        log.info("[webhook] Marked payment {} failed for booking {} (order {})", payment.getId(), booking.getReference(), paymentEntity.orderId());
    }
}
