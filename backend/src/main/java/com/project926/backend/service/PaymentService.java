package com.project926.backend.service;

import com.project926.backend.dto.CreateOrderRequest;
import com.project926.backend.dto.CreateOrderResponse;
import com.project926.backend.dto.VerifyPaymentRequest;
import com.project926.backend.dto.VerifyPaymentResponse;
import com.project926.backend.entity.Booking;
import com.project926.backend.entity.Event;
import com.project926.backend.entity.Payment;
import com.project926.backend.entity.TicketType;
import com.project926.backend.exception.BookingConfirmationException;
import com.project926.backend.exception.BookingValidationException;
import com.project926.backend.exception.ForbiddenException;
import com.project926.backend.exception.PaymentFlowNotFoundException;
import com.project926.backend.exception.RazorpayGatewayException;
import com.project926.backend.exception.SoldOutException;
import com.project926.backend.integration.razorpay.RazorpayGateway;
import com.project926.backend.repository.BookingInventoryRepository;
import com.project926.backend.repository.BookingRepository;
import com.project926.backend.repository.EventRepository;
import com.project926.backend.repository.PaymentRepository;
import com.project926.backend.repository.TicketTypeRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mirrors app/(project926)/project926/api/payments/create-order/route.ts
 * and .../payments/verify/route.ts exactly. See the Phase D report for the
 * full behavioral audit this was built from.
 *
 * Deliberately NOT @Transactional at the method level (neither
 * createOrder nor verifyPayment): the existing Next.js code never wraps
 * its sequence of Supabase calls (RPC, Razorpay HTTP call, payment insert/
 * update) in a shared database transaction either — each call commits
 * independently, and a failure partway through leaves exactly the partial
 * state the existing code would leave (e.g. a pending booking with no
 * Razorpay order if the Razorpay call fails — see Step 9 in the report).
 * Reproducing that requires NOT introducing a transaction boundary the
 * original never had.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final String APPROVED = "approved";
    private static final String INR = "INR";

    private final EventRepository eventRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final BookingInventoryRepository bookingInventoryRepository;
    private final RazorpayGateway razorpayGateway;

    public PaymentService(
        EventRepository eventRepository,
        TicketTypeRepository ticketTypeRepository,
        BookingRepository bookingRepository,
        PaymentRepository paymentRepository,
        BookingInventoryRepository bookingInventoryRepository,
        RazorpayGateway razorpayGateway
    ) {
        this.eventRepository = eventRepository;
        this.ticketTypeRepository = ticketTypeRepository;
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.bookingInventoryRepository = bookingInventoryRepository;
        this.razorpayGateway = razorpayGateway;
    }

    // ================= create-order =================

    public CreateOrderResponse createOrder(String customerId, CreateOrderRequest request) {
        Event event = eventRepository.findById(request.eventId())
            .orElseThrow(() -> new PaymentFlowNotFoundException("Event not found"));
        if (!APPROVED.equals(event.getStatus())) {
            throw new BookingValidationException("Event is not available for booking");
        }

        List<UUID> ticketTypeIds = request.items().stream().map(CreateOrderRequest.Item::ticketTypeId).toList();
        List<TicketType> ticketTypes = ticketTypeRepository.findAllById(ticketTypeIds);
        if (ticketTypes.size() != ticketTypeIds.size()) {
            throw new BookingValidationException("One or more ticket types not found");
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (CreateOrderRequest.Item item : request.items()) {
            TicketType tt = ticketTypes.stream()
                .filter(t -> t.getId().equals(item.ticketTypeId()))
                .findFirst()
                .orElseThrow(() -> new BookingValidationException("Ticket type " + item.ticketTypeId() + " not found"));

            int remaining = tt.getQuantityTotal() - tt.getQuantitySold();
            if (item.quantity() > remaining) {
                throw new BookingValidationException("Only " + remaining + " tickets left for " + tt.getName());
            }
            totalAmount = totalAmount.add(tt.getPrice().multiply(BigDecimal.valueOf(item.quantity())));
        }

        if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BookingValidationException("Invalid amount");
        }

        // Same JSON shape as the existing itemsPayload:
        // [{ ticket_type_id, quantity }, ...]
        JSONObject[] itemsArray = request.items().stream()
            .map(i -> {
                JSONObject o = new JSONObject();
                o.put("ticket_type_id", i.ticketTypeId().toString());
                o.put("quantity", i.quantity());
                return o;
            })
            .toArray(JSONObject[]::new);
        String itemsJson = new org.json.JSONArray(itemsArray).toString();

        // Auto-commits independently, exactly like the existing
        // supabaseAdmin.rpc(...) call — see class Javadoc.
        UUID bookingId = bookingInventoryRepository.createBookingFromItems(customerId, request.eventId(), itemsJson);
        if (bookingId == null) {
            throw new IllegalStateException("Failed to create booking");
        }

        long amountPaise = totalAmount.multiply(BigDecimal.valueOf(100))
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact();

        Map<String, String> notes = new LinkedHashMap<>();
        notes.put("booking_id", bookingId.toString());
        notes.put("event_id", request.eventId().toString());
        notes.put("customer_id", customerId);

        Order order;
        try {
            String receipt = "booking_" + bookingId.toString().substring(0, Math.min(24, bookingId.toString().length()));
            order = razorpayGateway.createOrder(amountPaise, INR, receipt, notes);
        } catch (RazorpayException e) {
            // Matches the existing route's behavior exactly: this isn't
            // caught specially, it falls through to the route's outer
            // try/catch -> generic 500. The booking created above is
            // deliberately left as-is (pending, with its 15-minute hold) —
            // no cleanup, matching the existing code (see Step 9).
            log.error("[create-order] Razorpay order creation failed", e);
            throw new IllegalStateException("Razorpay order creation failed", e);
        }
        String orderId = order.get("id");

        Payment payment = Payment.createForOrder(bookingId, orderId, totalAmount, INR);
        try {
            paymentRepository.save(payment);
        } catch (RuntimeException e) {
            log.error("Failed to create payment record", e);
            throw new IllegalStateException("Failed to initialize payment", e);
        }

        return new CreateOrderResponse(orderId, bookingId, amountPaise, INR, razorpayGateway.getPublicKeyId());
    }

    // ================= verify =================

    public VerifyPaymentResponse verifyPayment(String customerId, VerifyPaymentRequest request) {
        Booking booking = bookingRepository.findById(request.bookingId())
            .orElseThrow(() -> new PaymentFlowNotFoundException("Booking not found"));
        if (!booking.getCustomerId().equals(customerId)) {
            throw new ForbiddenException("Forbidden");
        }

        if ("confirmed".equals(booking.getStatus())) {
            // Idempotent fast path — matches the existing route exactly,
            // including returning whatever qr_code is already stored
            // (always null in Phase D — see VerifyPaymentResponse's Javadoc).
            return new VerifyPaymentResponse(true, booking.getId(), booking.getReference(), booking.getQrCode());
        }
        if (!"pending".equals(booking.getStatus())) {
            throw new BookingValidationException("Booking is " + booking.getStatus() + " and cannot be verified");
        }

        // Best-effort hold extension — logged, never fails the request,
        // exactly like the existing route's extendErr handling.
        try {
            bookingRepository.extendExpiresAtIfPending(booking.getId(), OffsetDateTime.now().plusMinutes(15));
        } catch (RuntimeException e) {
            log.error("[verify] Failed to extend booking hold: {}", e.getMessage());
        }

        Payment payment = paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(booking.getId())
            .orElseThrow(() -> new BookingValidationException("No payment record found for this booking"));

        // 1. Signature verification.
        if (!razorpayGateway.verifySignature(request.razorpayOrderId(), request.razorpayPaymentId(), request.razorpaySignature())) {
            rejectPayment(payment.getId(), booking.getId(), "invalid signature", booking.getReference());
            throw new BookingValidationException("Signature verification failed");
        }

        // 2. Order must match the one created for this booking.
        if (!request.razorpayOrderId().equals(payment.getRazorpayOrderId())) {
            rejectPayment(payment.getId(), booking.getId(), "order id does not match booking", booking.getReference());
            throw new BookingValidationException("Order does not match booking");
        }

        // 3-5. Fetch from Razorpay itself — never trust the client payload.
        com.razorpay.Payment rpPayment;
        try {
            rpPayment = razorpayGateway.fetchPayment(request.razorpayPaymentId());
        } catch (RazorpayException e) {
            // No rejectPayment() here — matches the existing route exactly:
            // a Razorpay-side fetch failure returns 502 without cancelling
            // anything, since we don't yet know the true payment state.
            log.error("[verify] Failed to fetch payment from Razorpay: {}", e.getMessage());
            throw new RazorpayGatewayException("Could not verify payment with Razorpay", e);
        }

        long expectedPaise = payment.getAmount()
            .multiply(BigDecimal.valueOf(100))
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact();

        String rpOrderId = rpPayment.get("order_id");
        if (!request.razorpayOrderId().equals(rpOrderId)) {
            rejectPayment(payment.getId(), booking.getId(), "razorpay payment does not belong to the expected order", booking.getReference());
            throw new BookingValidationException("Payment/order mismatch");
        }

        String rpStatus = rpPayment.get("status");
        if (!"captured".equals(rpStatus)) {
            rejectPayment(payment.getId(), booking.getId(), "razorpay payment status is " + rpStatus + ", not captured", booking.getReference());
            throw new BookingValidationException("Payment was not captured");
        }

        long rpAmount = ((Number) rpPayment.get("amount")).longValue();
        String rpCurrency = rpPayment.get("currency");
        if (rpAmount != expectedPaise || !rpCurrency.equals(payment.getCurrency())) {
            rejectPayment(payment.getId(), booking.getId(),
                "amount mismatch: expected " + expectedPaise + " " + payment.getCurrency() + ", got " + rpAmount + " " + rpCurrency,
                booking.getReference());
            throw new BookingValidationException("Payment amount mismatch");
        }

        // QR code generation is explicitly out of scope for Phase D — see
        // VerifyPaymentResponse's Javadoc. null is a valid value for the
        // nullable bookings.qr_code column.
        String qrCode = null;

        boolean newlyConfirmed;
        try {
            // Auto-commits independently, exactly like the existing
            // supabaseAdmin.rpc(...) call — see class Javadoc.
            newlyConfirmed = bookingInventoryRepository.confirmBookingAndCommitInventory(booking.getId(), qrCode);
        } catch (SoldOutException soldOut) {
            log.error(
                "[verify] CRITICAL: booking {} paid (razorpay payment {}) but sold out at confirmation — manual refund required: {}",
                booking.getReference(), request.razorpayPaymentId(), soldOut.getMessage()
            );
            paymentRepository.markPaid(payment.getId(), request.razorpayPaymentId(), request.razorpaySignature(), "paid");
            bookingRepository.updateStatus(booking.getId(), "cancelled");
            throw soldOut;
        } catch (BookingConfirmationException confirmErr) {
            log.error("[verify] Failed to confirm booking: {}", confirmErr.getMessage());
            throw confirmErr;
        }

        // Mark paid unconditionally — matches the existing route's comment:
        // whether newly confirmed or already confirmed by a concurrent
        // request, this payment did succeed.
        paymentRepository.markPaid(payment.getId(), request.razorpayPaymentId(), request.razorpaySignature(), "paid");

        if (newlyConfirmed) {
            log.info("[verify] Booking {} confirmed, inventory committed", booking.getReference());
        } else {
            log.info("[verify] Booking {} was already confirmed, skipping", booking.getReference());
        }

        // Resend email is explicitly out of scope for Phase D (Step 24) —
        // the existing route's email step is intentionally not reproduced
        // here; it never affects this response either way in the original.

        return new VerifyPaymentResponse(true, booking.getId(), booking.getReference(), qrCode);
    }

    private void rejectPayment(UUID paymentId, UUID bookingId, String reason, String reference) {
        log.error("[verify] Rejecting payment for booking {}: {}", reference, reason);
        paymentRepository.updateStatus(paymentId, "failed");
        bookingRepository.updateStatus(bookingId, "cancelled");
    }
}
