package com.project926.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project926.backend.dto.RazorpayWebhookEvent;
import com.project926.backend.integration.razorpay.RazorpayWebhookSignatureVerifier;
import com.project926.backend.service.RazorpayWebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * Public, machine-to-machine Razorpay webhook endpoint (Phase G) —
 * deliberately NOT behind Clerk authentication (see SecurityConfig's
 * narrowly-scoped permitAll for exactly this one path). The eventual
 * public URL (https://empreso.in/project926/api/webhooks/razorpay, per a
 * reverse proxy not configured in this phase) is expected to map onto
 * this endpoint, following the same {@code /api/v1/*} convention every
 * other endpoint in this migration already uses — no deployment/routing
 * configuration is changed here.
 *
 * Kept deliberately thin: signature verification, JSON parsing, minimal
 * shape validation, and HTTP status selection only. All business logic —
 * event interpretation, correlation, confirmation, idempotency — lives in
 * RazorpayWebhookService.
 *
 * <h2>Raw-body signature verification</h2>
 * The request body is captured as {@code byte[]} (never
 * {@code @RequestBody SomeDto}) specifically so the HMAC signature can be
 * computed over the EXACT bytes Razorpay sent, before any JSON parsing
 * happens. Parsing first and re-serializing to verify would risk
 * whitespace/key-order/number-formatting differences invalidating a
 * genuine signature — see RazorpayWebhookSignatureVerifier's Javadoc.
 * {@link ObjectMapper#readValue(byte[], Class)} is called only AFTER
 * {@link RazorpayWebhookSignatureVerifier#verify} returns {@code true}.
 *
 * <h2>HTTP response semantics</h2>
 * Chosen so Razorpay's automatic retry behavior is only ever exercised
 * where retrying could actually help:
 * <ul>
 *   <li>400 — invalid/missing signature, or a malformed/incomplete body
 *       that passed signature verification (both permanent conditions;
 *       retrying an identical delivery reaches the identical result).</li>
 *   <li>200 — every other outcome, including business states the service
 *       intentionally leaves unactioned (unknown event, no correlation,
 *       stale booking state, SOLD_OUT) — these are fully handled/logged
 *       already, not failures.</li>
 *   <li>500 — an unexpected internal failure while processing a
 *       recognized event (e.g. a database error, or an unexpected RPC
 *       failure surfaced as BookingConfirmationException) — genuinely
 *       worth Razorpay retrying.</li>
 * </ul>
 * Never logs the webhook secret, the signature value, or the full raw
 * payload — only event name and, once parsed, the safe Razorpay order/
 * payment ids already treated as non-secret elsewhere in this app (see
 * RazorpayGateway's Javadoc for the same policy).
 */
@RestController
@RequestMapping("/api/v1/webhooks/razorpay")
public class RazorpayWebhookController {

    private static final Logger log = LoggerFactory.getLogger(RazorpayWebhookController.class);

    private final RazorpayWebhookSignatureVerifier signatureVerifier;
    private final RazorpayWebhookService webhookService;
    private final ObjectMapper objectMapper;

    public RazorpayWebhookController(
        RazorpayWebhookSignatureVerifier signatureVerifier,
        RazorpayWebhookService webhookService,
        ObjectMapper objectMapper
    ) {
        this.signatureVerifier = signatureVerifier;
        this.webhookService = webhookService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<Void> handleWebhook(
        @RequestBody byte[] rawBody,
        @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature
    ) {
        if (!signatureVerifier.verify(rawBody, signature)) {
            log.warn("[webhook] Rejected delivery: invalid or missing X-Razorpay-Signature");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        RazorpayWebhookEvent event;
        try {
            event = objectMapper.readValue(rawBody, RazorpayWebhookEvent.class);
        } catch (IOException malformed) {
            log.warn("[webhook] Rejected delivery: malformed JSON body (signature was valid)");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        if (event.event() == null
            || event.payload() == null
            || event.payload().payment() == null
            || event.payload().payment().entity() == null) {
            log.warn("[webhook] Rejected delivery: missing required event/payload/payment fields");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        try {
            webhookService.process(event.event(), event.payload().payment().entity());
            return ResponseEntity.ok().build();
        } catch (Exception unexpected) {
            log.error("[webhook] Internal failure processing event {}", event.event(), unexpected);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
