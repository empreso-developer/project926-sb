package com.project926.backend.integration.razorpay;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Verifies a Razorpay webhook delivery per Razorpay's documented scheme:
 * {@code HMAC-SHA256(RAZORPAY_WEBHOOK_SECRET, raw_request_body)}, compared
 * against the {@code X-Razorpay-Signature} header using a constant-time
 * comparison (mirrors RazorpayGateway.verifySignature's established
 * pattern for the unrelated order|payment browser-flow signature — same
 * hex-decode/HMAC/MessageDigest.isEqual shape, deliberately NOT shared
 * code with that method since the two secrets, inputs, and trust
 * boundaries are entirely different: this one authenticates an inbound
 * server-to-server webhook over the exact raw body bytes, that one
 * authenticates a client-supplied "orderId|paymentId" string).
 *
 * CRITICAL: {@code rawBody} must be the exact bytes Razorpay sent — never
 * bytes produced by parsing the JSON and re-serializing it, which can
 * silently change whitespace/key order/number formatting and invalidate
 * every signature. See RazorpayWebhookController, which captures raw
 * bytes via {@code @RequestBody byte[]} specifically so this can run
 * before any JSON parsing happens.
 *
 * Never logs the webhook secret or the signature value.
 */
@Component
public class RazorpayWebhookSignatureVerifier {

    private final String webhookSecret;

    public RazorpayWebhookSignatureVerifier(@Value("${razorpay.webhook-secret}") String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    /**
     * @param rawBody         the exact raw HTTP request body bytes.
     * @param signatureHeader the {@code X-Razorpay-Signature} header value
     *                        (a hex-encoded HMAC-SHA256 digest), or
     *                        {@code null}/blank if absent — always
     *                        rejected in that case.
     */
    public boolean verify(byte[] rawBody, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        if (webhookSecret == null || webhookSecret.isBlank()) {
            // Misconfiguration, not a client-supplied condition — fail
            // closed rather than accepting an unverifiable delivery.
            return false;
        }

        byte[] provided;
        try {
            provided = hexDecode(signatureHeader);
        } catch (IllegalArgumentException malformedHex) {
            return false;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(rawBody);
            if (expected.length != provided.length) {
                return false;
            }
            return MessageDigest.isEqual(expected, provided);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Unable to compute Razorpay webhook signature", e);
        }
    }

    private static byte[] hexDecode(String hex) {
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Odd-length hex string");
        }
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("Invalid hex character");
            }
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }
}
