package com.project926.backend.service;

import com.project926.backend.entity.Booking;
import com.project926.backend.entity.BookingItem;
import com.project926.backend.entity.Event;
import com.project926.backend.entity.Payment;
import com.project926.backend.entity.Profile;
import com.project926.backend.entity.TicketType;
import com.project926.backend.integration.resend.ResendGateway;
import com.project926.backend.repository.BookingItemRepository;
import com.project926.backend.repository.BookingRepository;
import com.project926.backend.repository.EventRepository;
import com.project926.backend.repository.PaymentRepository;
import com.project926.backend.repository.ProfileRepository;
import com.project926.backend.repository.TicketTypeRepository;
import com.project926.backend.service.email.EmailFormatUtils;
import com.project926.backend.service.email.TicketConfirmationEmailData;
import com.project926.backend.service.email.TicketConfirmationEmailTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Mirrors lib/email/send-ticket-confirmation.ts's
 * sendBookingConfirmationEmailOnce exactly, including its idempotency
 * claim, data gathering, and never-throws contract — see
 * BookingRepository.claimTicketEmailSend's Javadoc for the atomic-claim
 * mechanism this reuses instead of a read-check-send-update sequence.
 *
 * Never throws: every failure path here is caught, logged, and recorded on
 * the booking row via recordTicketEmailError — callers (PaymentService)
 * must not let a failure here affect the payment/booking response, exactly
 * matching the existing route.
 */
@Service
public class TicketEmailService {

    private static final Logger log = LoggerFactory.getLogger(TicketEmailService.class);
    private static final String QR_CONTENT_ID = "ticket-qr-code";

    private final BookingRepository bookingRepository;
    private final EventRepository eventRepository;
    private final BookingItemRepository bookingItemRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final PaymentRepository paymentRepository;
    private final ProfileRepository profileRepository;
    private final ResendGateway resendGateway;
    private final String dashboardUrl;

    public TicketEmailService(
            BookingRepository bookingRepository,
            EventRepository eventRepository,
            BookingItemRepository bookingItemRepository,
            TicketTypeRepository ticketTypeRepository,
            PaymentRepository paymentRepository,
            ProfileRepository profileRepository,
            ResendGateway resendGateway,
            @Value("${app.public-url:http://localhost:3000}") String appPublicUrl) {
        this.bookingRepository = bookingRepository;
        this.eventRepository = eventRepository;
        this.bookingItemRepository = bookingItemRepository;
        this.ticketTypeRepository = ticketTypeRepository;
        this.paymentRepository = paymentRepository;
        this.profileRepository = profileRepository;
        this.resendGateway = resendGateway;
        this.dashboardUrl = appPublicUrl + "/p/dashboard/customer";
    }

    /**
     * @param qrCodeDataUrl the freshly-known QR data URL, passed explicitly
     *                      by the caller (PaymentService) — NOT read from
     *                      {@code booking.getQrCode()}, since the in-memory
     *                      Booking entity was loaded before
     *                      confirm_booking_and_commit_inventory persisted
     *                      the QR value and is therefore stale. This
     *                      mirrors the existing route exactly: it passes
     *                      its local {@code qrDataUrl} variable into
     *                      {@code sendBookingConfirmationEmailOnce}'s
     *                      {@code qr_code} field rather than re-reading the
     *                      booking row.
     */
    public void sendBookingConfirmationEmailOnce(Booking booking, String qrCodeDataUrl) {
        int claimed = bookingRepository.claimTicketEmailSend(booking.getId(), java.time.OffsetDateTime.now());
        if (claimed == 0) {
            log.info("[email/ticket] Confirmation email already sent for booking {}, skipping", booking.getReference());
            return;
        }

        try {
            log.info("[email/ticket] Sending confirmation email for booking {}", booking.getReference());

            Event event = eventRepository.findById(booking.getEventId()).orElse(null);
            Profile profile = profileRepository.findById(booking.getCustomerId()).orElse(null);
            if (event == null || profile == null) {
                throw new IllegalStateException("Missing booking or profile data for confirmation email");
            }

            List<BookingItem> items = bookingItemRepository.findByBookingId(booking.getId());
            List<UUID> ticketTypeIds = items.stream().map(BookingItem::getTicketTypeId).distinct().toList();
            Map<UUID, TicketType> ticketTypesById = ticketTypeRepository.findAllById(ticketTypeIds).stream()
                    .collect(Collectors.toMap(TicketType::getId, t -> t));

            Payment payment = paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(booking.getId()).orElse(null);

            TicketConfirmationEmailData emailData = new TicketConfirmationEmailData(
                    new TicketConfirmationEmailData.Customer(profile.getFirstName(), profile.getLastName(),
                            profile.getEmail()),
                    new TicketConfirmationEmailData.BookingInfo(booking.getId().toString(), booking.getReference(),
                            booking.getTotalAmount()),
                    new TicketConfirmationEmailData.EventInfo(
                            event.getTitle(),
                            EmailFormatUtils.formatDate(event.getEventDate()),
                            EmailFormatUtils.formatTime(event.getEventTime()),
                            event.getVenue(),
                            event.getCity(),
                            event.getBannerUrl()),
                    items.stream().map(item -> {
                        TicketType tt = ticketTypesById.get(item.getTicketTypeId());
                        return new TicketConfirmationEmailData.TicketLine(
                                tt != null ? tt.getName() : "Ticket",
                                item.getQuantity(),
                                item.getUnitPrice(),
                                item.getSubtotal());
                    }).toList(),
                    new TicketConfirmationEmailData.PaymentInfo(
                            payment != null ? payment.getAmount() : booking.getTotalAmount(),
                            payment != null ? payment.getCurrency() : "INR",
                            payment != null ? payment.getRazorpayPaymentId() : null),
                    qrCodeDataUrl,
                    dashboardUrl);

            sendTicketConfirmationEmail(emailData);
            log.info("[email/ticket] Confirmation email sent for booking {}", booking.getReference());
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : "Unknown error";
            log.error("[email/ticket] Failed to send confirmation email for booking {}: {}", booking.getReference(),
                    message, e);
            bookingRepository.recordTicketEmailError(booking.getId(),
                    message.length() > 500 ? message.substring(0, 500) : message);
        }
    }

    /**
     * Mirrors sendTicketConfirmationEmail: extracts the raw PNG bytes from
     * the stored "data:image/png;base64,..." data URL (never re-generates
     * the QR here — it's already persisted on the booking), renders the
     * template, and sends via ResendGateway with the QR as an inline CID
     * attachment.
     */
    private void sendTicketConfirmationEmail(TicketConfirmationEmailData data) {
        String qrDataUrl = data.qrCodeDataUrl();
        int commaIndex = qrDataUrl != null ? qrDataUrl.indexOf(',') : -1;
        byte[] qrBytes = commaIndex >= 0
                ? Base64.getDecoder().decode(qrDataUrl.substring(commaIndex + 1))
                : new byte[0];

        var rendered = TicketConfirmationEmailTemplate.render(data, QR_CONTENT_ID);

        resendGateway.sendEmail(
                data.customer().email(),
                rendered.subject(),
                rendered.html(),
                qrBytes,
                "ticket-qr-code.png",
                "image/png",
                QR_CONTENT_ID);
    }
}
