package com.project926.backend.service;

import com.project926.backend.dto.CreateOrderRequest;
import com.project926.backend.dto.CreateOrderResponse;
import com.project926.backend.dto.VerifyPaymentRequest;
import com.project926.backend.dto.VerifyPaymentResponse;
import com.project926.backend.entity.Booking;
import com.project926.backend.entity.Event;
import com.project926.backend.entity.Payment;
import com.project926.backend.entity.TicketType;
import com.project926.backend.exception.BookingValidationException;
import com.project926.backend.exception.ForbiddenException;
import com.project926.backend.exception.PaymentFlowNotFoundException;
import com.project926.backend.exception.RazorpayGatewayException;
import com.project926.backend.exception.SoldOutException;
import com.project926.backend.integration.qrcode.QrCodeGenerator;
import com.project926.backend.integration.razorpay.RazorpayGateway;
import com.project926.backend.repository.BookingInventoryRepository;
import com.project926.backend.repository.BookingRepository;
import com.project926.backend.repository.EventRepository;
import com.project926.backend.repository.PaymentRepository;
import com.project926.backend.repository.TicketTypeRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mirrors app/(project926)/p/api/payments/create-order/route.ts
 * and .../payments/verify/route.ts, unit-level: every repository and
 * RazorpayGateway call is mocked, so this never touches a real database or
 * a real Razorpay endpoint (Step 21's explicit requirement).
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private EventRepository eventRepository;
    @Mock
    private TicketTypeRepository ticketTypeRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private BookingInventoryRepository bookingInventoryRepository;
    @Mock
    private RazorpayGateway razorpayGateway;
    @Mock
    private QrCodeGenerator qrCodeGenerator;
    @Mock
    private TicketEmailService ticketEmailService;

    private PaymentService service() {
        return new PaymentService(eventRepository, ticketTypeRepository, bookingRepository,
                paymentRepository, bookingInventoryRepository, razorpayGateway, qrCodeGenerator, ticketEmailService);
    }

    private static final String CUSTOMER_ID = "user_customer000000000000";

    // ---- fixtures --------------------------------------------------------

    private Event event(UUID id, String status) {
        Event e = newInstance(Event.class);
        set(e, "id", id);
        set(e, "status", status);
        return e;
    }

    private TicketType ticketType(UUID id, String name, BigDecimal price, int total, int sold) {
        TicketType t = newInstance(TicketType.class);
        set(t, "id", id);
        set(t, "name", name);
        set(t, "price", price);
        set(t, "quantityTotal", total);
        set(t, "quantitySold", sold);
        return t;
    }

    private Booking booking(UUID id, String customerId, String status, String reference, String qrCode) {
        Booking b = newInstance(Booking.class);
        set(b, "id", id);
        set(b, "customerId", customerId);
        set(b, "status", status);
        set(b, "reference", reference);
        set(b, "qrCode", qrCode);
        set(b, "totalAmount", new BigDecimal("500.00"));
        return b;
    }

    private Payment payment(UUID id, UUID bookingId, String razorpayOrderId, BigDecimal amount, String currency,
            String status) {
        Payment p = newInstance(Payment.class);
        set(p, "id", id);
        set(p, "bookingId", bookingId);
        set(p, "razorpayOrderId", razorpayOrderId);
        set(p, "amount", amount);
        set(p, "currency", currency);
        set(p, "status", status);
        return p;
    }

    private com.razorpay.Payment rpPayment(String orderId, String status, long amountPaise, String currency) {
        JSONObject json = new JSONObject();
        json.put("id", "pay_test123");
        json.put("order_id", orderId);
        json.put("status", status);
        json.put("amount", amountPaise);
        json.put("currency", currency);
        return new com.razorpay.Payment(json);
    }

    private CreateOrderRequest.Item item(UUID ticketTypeId, int quantity) {
        return new CreateOrderRequest.Item(ticketTypeId, quantity);
    }

    // ---- createOrder -------------------------------------------------------

    @Test
    void createOrder_eventNotFound_throwsPaymentFlowNotFound() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().createOrder(CUSTOMER_ID,
                new CreateOrderRequest(eventId, List.of(item(UUID.randomUUID(), 1)))))
                .isInstanceOf(PaymentFlowNotFoundException.class)
                .hasMessage("Event not found");
    }

    @Test
    void createOrder_eventNotApproved_throwsBookingValidation() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event(eventId, "draft")));

        assertThatThrownBy(() -> service().createOrder(CUSTOMER_ID,
                new CreateOrderRequest(eventId, List.of(item(UUID.randomUUID(), 1)))))
                .isInstanceOf(BookingValidationException.class)
                .hasMessage("Event is not available for booking");
    }

    @Test
    void createOrder_ticketTypeCountMismatch_throwsBookingValidation() {
        UUID eventId = UUID.randomUUID();
        UUID ttId = UUID.randomUUID();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event(eventId, "approved")));
        when(ticketTypeRepository.findAllById(List.of(ttId))).thenReturn(List.of()); // none found

        assertThatThrownBy(() -> service().createOrder(CUSTOMER_ID,
                new CreateOrderRequest(eventId, List.of(item(ttId, 1)))))
                .isInstanceOf(BookingValidationException.class)
                .hasMessage("One or more ticket types not found");
    }

    @Test
    void createOrder_insufficientQuantity_throwsBookingValidationWithRemainingCount() {
        UUID eventId = UUID.randomUUID();
        UUID ttId = UUID.randomUUID();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event(eventId, "approved")));
        when(ticketTypeRepository.findAllById(List.of(ttId)))
                .thenReturn(List.of(ticketType(ttId, "VIP", new BigDecimal("100.00"), 10, 8))); // 2 remaining

        assertThatThrownBy(() -> service().createOrder(CUSTOMER_ID,
                new CreateOrderRequest(eventId, List.of(item(ttId, 5)))))
                .isInstanceOf(BookingValidationException.class)
                .hasMessage("Only 2 tickets left for VIP");
    }

    @Test
    void createOrder_success_computesAmountServerSide_andReturnsExpectedFields() throws RazorpayException {
        UUID eventId = UUID.randomUUID();
        UUID ttId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event(eventId, "approved")));
        when(ticketTypeRepository.findAllById(List.of(ttId)))
                .thenReturn(List.of(ticketType(ttId, "General", new BigDecimal("250.50"), 100, 0)));
        when(bookingInventoryRepository.createBookingFromItems(eq(CUSTOMER_ID), eq(eventId), anyString()))
                .thenReturn(bookingId);
        when(razorpayGateway.createOrder(eq(50100L), eq("INR"), anyString(), anyMap()))
                .thenReturn(new Order(new JSONObject().put("id", "order_abc123")));
        when(razorpayGateway.getPublicKeyId()).thenReturn("rzp_test_public");

        CreateOrderResponse response = service().createOrder(CUSTOMER_ID,
                new CreateOrderRequest(eventId, List.of(item(ttId, 2)))); // 2 * 250.50 = 501.00 -> 50100 paise

        assertThat(response.orderId()).isEqualTo("order_abc123");
        assertThat(response.bookingId()).isEqualTo(bookingId);
        assertThat(response.amount()).isEqualTo(50100L);
        assertThat(response.currency()).isEqualTo("INR");
        assertThat(response.keyId()).isEqualTo("rzp_test_public");
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void createOrder_ignoresAnyClientSuppliedAmount_priceIsAlwaysFromDatabase() {
        // Structural proof: CreateOrderRequest has no amount/price field at
        // all, so the server-side price lookup above is the only source —
        // this is enforced by the DTO shape, not a runtime check.
        assertThat(CreateOrderRequest.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("eventId", "items");
    }

    @Test
    void createOrder_razorpayOrderCreationFails_bookingIsNotCleanedUp() throws RazorpayException {
        UUID eventId = UUID.randomUUID();
        UUID ttId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event(eventId, "approved")));
        when(ticketTypeRepository.findAllById(List.of(ttId)))
                .thenReturn(List.of(ticketType(ttId, "General", new BigDecimal("100.00"), 100, 0)));
        when(bookingInventoryRepository.createBookingFromItems(eq(CUSTOMER_ID), eq(eventId), anyString()))
                .thenReturn(bookingId);
        when(razorpayGateway.createOrder(anyLong(), anyString(), anyString(), anyMap()))
                .thenThrow(new RazorpayException("network error"));

        assertThatThrownBy(() -> service().createOrder(CUSTOMER_ID,
                new CreateOrderRequest(eventId, List.of(item(ttId, 1)))))
                .isInstanceOf(IllegalStateException.class);

        // The booking RPC already committed independently before the
        // Razorpay call — no compensating cancellation exists, matching
        // the existing route exactly (see Step 9 in the report).
        verify(bookingRepository, never()).updateStatus(any(), any());
        verify(paymentRepository, never()).save(any());
    }

    // ---- verifyPayment -----------------------------------------------------

    @Test
    void verifyPayment_bookingNotFound_throwsPaymentFlowNotFound() {
        UUID bookingId = UUID.randomUUID();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().verifyPayment(CUSTOMER_ID,
                new VerifyPaymentRequest("order_x", "pay_x", "sig_x", bookingId)))
                .isInstanceOf(PaymentFlowNotFoundException.class)
                .hasMessage("Booking not found");
    }

    @Test
    void verifyPayment_notOwnedByCaller_throwsForbidden() {
        UUID bookingId = UUID.randomUUID();
        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.of(booking(bookingId, "user_someoneElse000000000", "pending", "BK-1", null)));

        assertThatThrownBy(() -> service().verifyPayment(CUSTOMER_ID,
                new VerifyPaymentRequest("order_x", "pay_x", "sig_x", bookingId)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void verifyPayment_alreadyConfirmed_returnsIdempotentSuccess_withoutTouchingRazorpay() {
        UUID bookingId = UUID.randomUUID();
        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.of(booking(bookingId, CUSTOMER_ID, "confirmed", "BK-1", null)));

        VerifyPaymentResponse response = service().verifyPayment(CUSTOMER_ID,
                new VerifyPaymentRequest("order_x", "pay_x", "sig_x", bookingId));

        assertThat(response.success()).isTrue();
        assertThat(response.reference()).isEqualTo("BK-1");
        verify(razorpayGateway, never()).verifySignature(any(), any(), any());
        verify(bookingInventoryRepository, never()).confirmBookingAndCommitInventory(any(), any());
    }

    @Test
    void verifyPayment_cancelledBooking_throwsBookingValidationWithDynamicStatus() {
        UUID bookingId = UUID.randomUUID();
        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.of(booking(bookingId, CUSTOMER_ID, "cancelled", "BK-1", null)));

        assertThatThrownBy(() -> service().verifyPayment(CUSTOMER_ID,
                new VerifyPaymentRequest("order_x", "pay_x", "sig_x", bookingId)))
                .isInstanceOf(BookingValidationException.class)
                .hasMessage("Booking is cancelled and cannot be verified");
    }

    @Test
    void verifyPayment_noPaymentRecord_throwsBookingValidation() {
        UUID bookingId = UUID.randomUUID();
        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.of(booking(bookingId, CUSTOMER_ID, "pending", "BK-1", null)));
        when(paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(bookingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().verifyPayment(CUSTOMER_ID,
                new VerifyPaymentRequest("order_x", "pay_x", "sig_x", bookingId)))
                .isInstanceOf(BookingValidationException.class)
                .hasMessage("No payment record found for this booking");
    }

    @Test
    void verifyPayment_invalidSignature_rejectsPaymentAndThrows() {
        UUID bookingId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.of(booking(bookingId, CUSTOMER_ID, "pending", "BK-1", null)));
        when(paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(bookingId))
                .thenReturn(Optional
                        .of(payment(paymentId, bookingId, "order_x", new BigDecimal("500.00"), "INR", "created")));
        when(razorpayGateway.verifySignature("order_x", "pay_x", "sig_x")).thenReturn(false);

        assertThatThrownBy(() -> service().verifyPayment(CUSTOMER_ID,
                new VerifyPaymentRequest("order_x", "pay_x", "sig_x", bookingId)))
                .isInstanceOf(BookingValidationException.class)
                .hasMessage("Signature verification failed");

        verify(paymentRepository).updateStatus(paymentId, "failed");
        verify(bookingRepository).updateStatus(bookingId, "cancelled");
    }

    @Test
    void verifyPayment_orderIdMismatch_rejectsPaymentAndThrows() {
        UUID bookingId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.of(booking(bookingId, CUSTOMER_ID, "pending", "BK-1", null)));
        when(paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(bookingId))
                .thenReturn(Optional.of(
                        payment(paymentId, bookingId, "order_DIFFERENT", new BigDecimal("500.00"), "INR", "created")));
        when(razorpayGateway.verifySignature(any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service().verifyPayment(CUSTOMER_ID,
                new VerifyPaymentRequest("order_x", "pay_x", "sig_x", bookingId)))
                .isInstanceOf(BookingValidationException.class)
                .hasMessage("Order does not match booking");

        verify(paymentRepository).updateStatus(paymentId, "failed");
    }

    @Test
    void verifyPayment_razorpayFetchFails_throwsGatewayException_withoutRejecting() throws RazorpayException {
        UUID bookingId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.of(booking(bookingId, CUSTOMER_ID, "pending", "BK-1", null)));
        when(paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(bookingId))
                .thenReturn(Optional
                        .of(payment(paymentId, bookingId, "order_x", new BigDecimal("500.00"), "INR", "created")));
        when(razorpayGateway.verifySignature(any(), any(), any())).thenReturn(true);
        when(razorpayGateway.fetchPayment("pay_x")).thenThrow(new RazorpayException("timeout"));

        assertThatThrownBy(() -> service().verifyPayment(CUSTOMER_ID,
                new VerifyPaymentRequest("order_x", "pay_x", "sig_x", bookingId)))
                .isInstanceOf(RazorpayGatewayException.class);

        // Critical: no rejectPayment on a gateway-fetch failure, matching
        // the existing route exactly.
        verify(paymentRepository, never()).updateStatus(any(), any());
        verify(bookingRepository, never()).updateStatus(any(), any());
    }

    @Test
    void verifyPayment_rpPaymentOrderMismatch_rejectsAndThrows() throws RazorpayException {
        UUID bookingId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.of(booking(bookingId, CUSTOMER_ID, "pending", "BK-1", null)));
        when(paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(bookingId))
                .thenReturn(Optional
                        .of(payment(paymentId, bookingId, "order_x", new BigDecimal("500.00"), "INR", "created")));
        when(razorpayGateway.verifySignature(any(), any(), any())).thenReturn(true);
        when(razorpayGateway.fetchPayment("pay_x")).thenReturn(rpPayment("order_DIFFERENT", "captured", 50000, "INR"));

        assertThatThrownBy(() -> service().verifyPayment(CUSTOMER_ID,
                new VerifyPaymentRequest("order_x", "pay_x", "sig_x", bookingId)))
                .isInstanceOf(BookingValidationException.class)
                .hasMessage("Payment/order mismatch");

        verify(paymentRepository).updateStatus(paymentId, "failed");
        verify(bookingRepository).updateStatus(bookingId, "cancelled");
    }

    @Test
    void verifyPayment_notCaptured_rejectsAndThrows() throws RazorpayException {
        UUID bookingId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.of(booking(bookingId, CUSTOMER_ID, "pending", "BK-1", null)));
        when(paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(bookingId))
                .thenReturn(Optional
                        .of(payment(paymentId, bookingId, "order_x", new BigDecimal("500.00"), "INR", "created")));
        when(razorpayGateway.verifySignature(any(), any(), any())).thenReturn(true);
        when(razorpayGateway.fetchPayment("pay_x")).thenReturn(rpPayment("order_x", "authorized", 50000, "INR"));

        assertThatThrownBy(() -> service().verifyPayment(CUSTOMER_ID,
                new VerifyPaymentRequest("order_x", "pay_x", "sig_x", bookingId)))
                .isInstanceOf(BookingValidationException.class)
                .hasMessage("Payment was not captured");
    }

    @Test
    void verifyPayment_amountMismatch_rejectsAndThrows() throws RazorpayException {
        UUID bookingId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.of(booking(bookingId, CUSTOMER_ID, "pending", "BK-1", null)));
        when(paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(bookingId))
                .thenReturn(Optional
                        .of(payment(paymentId, bookingId, "order_x", new BigDecimal("500.00"), "INR", "created")));
        when(razorpayGateway.verifySignature(any(), any(), any())).thenReturn(true);
        // Expected 50000 paise (500.00 INR), Razorpay says 40000.
        when(razorpayGateway.fetchPayment("pay_x")).thenReturn(rpPayment("order_x", "captured", 40000, "INR"));

        assertThatThrownBy(() -> service().verifyPayment(CUSTOMER_ID,
                new VerifyPaymentRequest("order_x", "pay_x", "sig_x", bookingId)))
                .isInstanceOf(BookingValidationException.class)
                .hasMessage("Payment amount mismatch");
    }

    @Test
    void verifyPayment_currencyMismatch_rejectsAndThrows() throws RazorpayException {
        UUID bookingId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.of(booking(bookingId, CUSTOMER_ID, "pending", "BK-1", null)));
        when(paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(bookingId))
                .thenReturn(Optional
                        .of(payment(paymentId, bookingId, "order_x", new BigDecimal("500.00"), "INR", "created")));
        when(razorpayGateway.verifySignature(any(), any(), any())).thenReturn(true);
        when(razorpayGateway.fetchPayment("pay_x")).thenReturn(rpPayment("order_x", "captured", 50000, "USD"));

        assertThatThrownBy(() -> service().verifyPayment(CUSTOMER_ID,
                new VerifyPaymentRequest("order_x", "pay_x", "sig_x", bookingId)))
                .isInstanceOf(BookingValidationException.class)
                .hasMessage("Payment amount mismatch");
    }

    @Test
    void verifyPayment_soldOutDuringConfirmation_marksPaymentPaidAndCancelsBooking_thenThrows()
            throws RazorpayException {
        UUID bookingId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.of(booking(bookingId, CUSTOMER_ID, "pending", "BK-1", null)));
        when(paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(bookingId))
                .thenReturn(Optional
                        .of(payment(paymentId, bookingId, "order_x", new BigDecimal("500.00"), "INR", "created")));
        when(razorpayGateway.verifySignature(any(), any(), any())).thenReturn(true);
        when(razorpayGateway.fetchPayment("pay_x")).thenReturn(rpPayment("order_x", "captured", 50000, "INR"));
        when(bookingInventoryRepository.confirmBookingAndCommitInventory(eq(bookingId), any()))
                .thenThrow(new SoldOutException("SOLD_OUT: not enough tickets remaining"));

        assertThatThrownBy(() -> service().verifyPayment(CUSTOMER_ID,
                new VerifyPaymentRequest("order_x", "pay_x", "sig_x", bookingId)))
                .isInstanceOf(SoldOutException.class);

        // Money is real (captured) -> payment marked paid even though the
        // booking itself is cancelled, exactly matching the existing route.
        verify(paymentRepository).markPaid(paymentId, "pay_x", "sig_x", "paid");
        verify(bookingRepository).updateStatus(bookingId, "cancelled");
    }

    @Test
    void verifyPayment_success_generatesQrPersistsItAndSendsEmail() throws RazorpayException {
        UUID bookingId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        Booking pendingBooking = booking(bookingId, CUSTOMER_ID, "pending", "BK-1", null);
        String fakeQrDataUrl = "data:image/png;base64,ZmFrZS1xci1ieXRlcw==";
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(pendingBooking));
        when(paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(bookingId))
                .thenReturn(Optional
                        .of(payment(paymentId, bookingId, "order_x", new BigDecimal("500.00"), "INR", "created")));
        when(razorpayGateway.verifySignature(any(), any(), any())).thenReturn(true);
        when(razorpayGateway.fetchPayment("pay_x")).thenReturn(rpPayment("order_x", "captured", 50000, "INR"));
        when(qrCodeGenerator.generateDataUrl("BK-1", bookingId, pendingBooking.getEventId())).thenReturn(fakeQrDataUrl);
        when(bookingInventoryRepository.confirmBookingAndCommitInventory(bookingId, fakeQrDataUrl)).thenReturn(true);

        VerifyPaymentResponse response = service().verifyPayment(CUSTOMER_ID,
                new VerifyPaymentRequest("order_x", "pay_x", "sig_x", bookingId));

        assertThat(response.success()).isTrue();
        assertThat(response.bookingId()).isEqualTo(bookingId);
        assertThat(response.reference()).isEqualTo("BK-1");
        // The QR is generated BEFORE confirm_booking_and_commit_inventory
        // and passed as its p_qr_code param — proven by the stub above
        // only matching that exact value — and the same freshly-generated
        // value (not a stale booking.getQrCode()) is what's returned to
        // the client and handed to the email service.
        assertThat(response.qrCode()).isEqualTo(fakeQrDataUrl);
        verify(paymentRepository).markPaid(paymentId, "pay_x", "sig_x", "paid");
        verify(ticketEmailService).sendBookingConfirmationEmailOnce(pendingBooking, fakeQrDataUrl);
    }

    @Test
    void verifyPayment_emailFailureDoesNotFailTheOtherwiseSuccessfulResponse() throws RazorpayException {
        UUID bookingId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        Booking pendingBooking = booking(bookingId, CUSTOMER_ID, "pending", "BK-1", null);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(pendingBooking));
        when(paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(bookingId))
                .thenReturn(Optional
                        .of(payment(paymentId, bookingId, "order_x", new BigDecimal("500.00"), "INR", "created")));
        when(razorpayGateway.verifySignature(any(), any(), any())).thenReturn(true);
        when(razorpayGateway.fetchPayment("pay_x")).thenReturn(rpPayment("order_x", "captured", 50000, "INR"));
        when(bookingInventoryRepository.confirmBookingAndCommitInventory(any(), any())).thenReturn(true);
        // TicketEmailService itself never throws (it catches its own
        // errors) — this simulates the redundant belt-and-suspenders case
        // where it somehow does, proving PaymentService's own try/catch
        // still protects the response.
        org.mockito.Mockito.doThrow(new RuntimeException("resend down"))
                .when(ticketEmailService).sendBookingConfirmationEmailOnce(any(), any());

        VerifyPaymentResponse response = service().verifyPayment(CUSTOMER_ID,
                new VerifyPaymentRequest("order_x", "pay_x", "sig_x", bookingId));

        assertThat(response.success()).isTrue();
        verify(paymentRepository).markPaid(paymentId, "pay_x", "sig_x", "paid");
    }

    @Test
    void verifyPayment_duplicateConfirmation_stillMarksPaymentPaid_idempotently() throws RazorpayException {
        // confirmBookingAndCommitInventory returning false means "already
        // confirmed by a concurrent/earlier call" — the route still marks
        // payment paid unconditionally per its own comment.
        UUID bookingId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.of(booking(bookingId, CUSTOMER_ID, "pending", "BK-1", null)));
        when(paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(bookingId))
                .thenReturn(Optional
                        .of(payment(paymentId, bookingId, "order_x", new BigDecimal("500.00"), "INR", "created")));
        when(razorpayGateway.verifySignature(any(), any(), any())).thenReturn(true);
        when(razorpayGateway.fetchPayment("pay_x")).thenReturn(rpPayment("order_x", "captured", 50000, "INR"));
        when(bookingInventoryRepository.confirmBookingAndCommitInventory(eq(bookingId), any())).thenReturn(false);

        VerifyPaymentResponse response = service().verifyPayment(CUSTOMER_ID,
                new VerifyPaymentRequest("order_x", "pay_x", "sig_x", bookingId));

        assertThat(response.success()).isTrue();
        verify(paymentRepository).markPaid(paymentId, "pay_x", "sig_x", "paid");
    }

    // ---- reflection helpers ------------------------------------------------

    private static void set(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static <T> T newInstance(Class<T> type) {
        try {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
