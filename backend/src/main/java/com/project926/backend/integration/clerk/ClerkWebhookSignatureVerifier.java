package com.project926.backend.integration.clerk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Verifies a Clerk webhook delivery per Svix's documented signing scheme
 * (Clerk webhooks are Svix webhooks — the existing Next.js route used the
 * official {@code svix} npm package's {@code Webhook.verify()}, which
 * implements exactly this algorithm: see
 * https://docs.svix.com/receiving/verifying-payloads/how-manual).
 * Reimplemented here by hand — same reasoning as
 * {@link com.project926.backend.integration.razorpay.RazorpayWebhookSignatureVerifier}:
 * a maintained Java Svix SDK was not already a dependency, the algorithm
 * is short, public, and precisely documented, and this codebase's
 * established preference is a small hand-rolled verifier over a new SDK
 * dependency for exactly this kind of thing (see also
 * SupabaseStorageGateway's Javadoc).
 *
 * <h2>Algorithm</h2>
 * <ol>
 * <li>The secret is {@code whsec_<base64>} — strip the prefix, base64-decode
 * the rest to get the raw HMAC key bytes.</li>
 * <li>The signed content is exactly {@code "{svix-id}.{svix-timestamp}.{raw body}"}
 * (UTF-8), never a re-serialized/parsed body.</li>
 * <li>The expected signature is {@code base64(HMAC-SHA256(key, signed content))}.</li>
 * <li>The {@code svix-signature} header holds one or more space-separated
 * {@code "v1,<base64 signature>"} tokens (Svix supports multiple active
 * secrets during rotation) — a match against ANY token is a valid
 * signature.</li>
 * <li>The delivery is rejected if {@code svix-timestamp} is more than 5
 * minutes from the current time in either direction (Svix's documented
 * replay-protection tolerance), independent of whether the signature
 * itself matches.</li>
 * </ol>
 *
 * Never logs the webhook secret or any signature value (matches
 * RazorpayWebhookSignatureVerifier's policy).
 */
@Component
public class ClerkWebhookSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(ClerkWebhookSignatureVerifier.class);
    private static final String SECRET_PREFIX = "whsec_";
    private static final long TOLERANCE_SECONDS = 5 * 60;

    private final String webhookSecret;

    public ClerkWebhookSignatureVerifier(@Value("${clerk.webhook-secret:}") String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    /**
     * @param rawBody      the exact raw HTTP request body bytes.
     * @param svixId       the {@code svix-id} header value.
     * @param svixTimestamp the {@code svix-timestamp} header value (Unix
     *                     seconds, as a string).
     * @param svixSignature the {@code svix-signature} header value (one or
     *                     more space-separated {@code "v1,<sig>"} tokens).
     */
    public boolean verify(byte[] rawBody, String svixId, String svixTimestamp, String svixSignature) {
        if (svixId == null || svixId.isBlank()
                || svixTimestamp == null || svixTimestamp.isBlank()
                || svixSignature == null || svixSignature.isBlank()) {
            return false;
        }
        if (webhookSecret == null || webhookSecret.isBlank() || !webhookSecret.startsWith(SECRET_PREFIX)) {
            // Misconfiguration, not a client-supplied condition — fail
            // closed rather than accepting an unverifiable delivery (same
            // policy as RazorpayWebhookSignatureVerifier).
            return false;
        }

        long timestampSeconds;
        try {
            timestampSeconds = Long.parseLong(svixTimestamp);
        } catch (NumberFormatException notANumber) {
            return false;
        }
        long nowSeconds = Instant.now().getEpochSecond();
        if (Math.abs(nowSeconds - timestampSeconds) > TOLERANCE_SECONDS) {
            log.warn("[clerk-webhook] Rejected delivery: timestamp outside the {}s tolerance window", TOLERANCE_SECONDS);
            return false;
        }

        byte[] secretBytes;
        try {
            secretBytes = Base64.getDecoder().decode(webhookSecret.substring(SECRET_PREFIX.length()));
        } catch (IllegalArgumentException malformedSecret) {
            log.error("[clerk-webhook] Configured webhook secret is not valid base64 after the whsec_ prefix");
            return false;
        }

        byte[] expected;
        try {
            String signedContent = svixId + "." + svixTimestamp + ".";
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
            mac.update(signedContent.getBytes(StandardCharsets.UTF_8));
            expected = mac.doFinal(rawBody);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Unable to compute Clerk webhook signature", e);
        }

        for (String token : svixSignature.trim().split("\\s+")) {
            int comma = token.indexOf(',');
            if (comma < 0) continue;
            String version = token.substring(0, comma);
            if (!"v1".equals(version)) continue;

            byte[] provided;
            try {
                provided = Base64.getDecoder().decode(token.substring(comma + 1));
            } catch (IllegalArgumentException malformedToken) {
                continue;
            }
            if (provided.length == expected.length && MessageDigest.isEqual(expected, provided)) {
                return true;
            }
        }
        return false;
    }
}
