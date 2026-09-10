package com.project926.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project926.backend.dto.ClerkWebhookEvent;
import com.project926.backend.integration.clerk.ClerkWebhookSignatureVerifier;
import com.project926.backend.service.ClerkWebhookService;
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
 * The new authoritative Clerk webhook receiver, replacing
 * app/(project926)/p/api/webhooks/clerk/route.ts's direct Supabase writes
 * (that route becomes a thin raw-body-preserving proxy to this endpoint —
 * see its own updated Javadoc). Public, machine-to-machine, deliberately
 * NOT behind Clerk JWT authentication (see SecurityConfig's narrowly-scoped
 * permitAll for exactly this one path) — its own security is Svix
 * signature verification, performed here before any parsing, mirroring
 * RazorpayWebhookController's structure exactly (raw {@code byte[]} body,
 * parse only after a verified signature, same HTTP-status philosophy: 400
 * for a permanently-invalid delivery, 200 for a fully-handled outcome
 * including intentionally-ignored event types, 500 for a genuinely
 * retry-worthy internal failure).
 *
 * <h2>Raw-body signature verification</h2>
 * The body is captured as {@code byte[]} (never {@code @RequestBody SomeDto})
 * so the HMAC is computed over the EXACT bytes Clerk sent — see
 * ClerkWebhookSignatureVerifier's Javadoc for why re-parsing/re-serializing
 * first would risk invalidating a genuine signature.
 */
@RestController
@RequestMapping("/api/v1/webhooks/clerk")
public class ClerkWebhookController {

    private static final Logger log = LoggerFactory.getLogger(ClerkWebhookController.class);

    private final ClerkWebhookSignatureVerifier signatureVerifier;
    private final ClerkWebhookService webhookService;
    private final ObjectMapper objectMapper;

    public ClerkWebhookController(
            ClerkWebhookSignatureVerifier signatureVerifier,
            ClerkWebhookService webhookService,
            ObjectMapper objectMapper) {
        this.signatureVerifier = signatureVerifier;
        this.webhookService = webhookService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<Void> handleWebhook(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "svix-id", required = false) String svixId,
            @RequestHeader(value = "svix-timestamp", required = false) String svixTimestamp,
            @RequestHeader(value = "svix-signature", required = false) String svixSignature) {
        if (!signatureVerifier.verify(rawBody, svixId, svixTimestamp, svixSignature)) {
            log.warn("[clerk-webhook] Rejected delivery: invalid or missing svix signature headers");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        ClerkWebhookEvent event;
        try {
            event = objectMapper.readValue(rawBody, ClerkWebhookEvent.class);
        } catch (IOException malformed) {
            log.warn("[clerk-webhook] Rejected delivery: malformed JSON body (signature was valid)");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        if (event.type() == null || event.data() == null || event.data().id() == null) {
            log.warn("[clerk-webhook] Rejected delivery: missing required type/data/data.id fields");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        try {
            webhookService.process(event.type(), event.data());
            return ResponseEntity.ok().build();
        } catch (Exception unexpected) {
            log.error("[clerk-webhook] Internal failure processing event {}", event.type(), unexpected);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
