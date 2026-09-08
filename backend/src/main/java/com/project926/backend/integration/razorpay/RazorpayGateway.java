package com.project926.backend.integration.razorpay;

import com.razorpay.Order;
import com.razorpay.Payment;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Mirrors lib/razorpay/server.ts exactly: the same two capabilities (an
 * order/payments client, and HMAC-SHA256 signature verification), backed by
 * the official Razorpay Java SDK (com.razorpay:razorpay-java) — the direct
 * counterpart of the existing app's official `razorpay` npm package. No
 * extra abstraction over what the SDK already provides.
 *
 * Never logs the key secret, a full signature, or any other credential —
 * only order/payment ids (which Razorpay itself treats as non-secret,
 * exactly like the existing [verify] log lines).
 */
@Component
public class RazorpayGateway {

    private final RazorpayClient client;
    private final String keySecret;
    private final String publicKeyId;

    public RazorpayGateway(
        @Value("${razorpay.key-id}") String keyId,
        @Value("${razorpay.key-secret}") String keySecret,
        @Value("${razorpay.public-key-id}") String publicKeyId
    ) throws RazorpayException {
        this.client = new RazorpayClient(keyId, keySecret);
        this.keySecret = keySecret;
        this.publicKeyId = publicKeyId;
    }

    /** The value the existing create-order response calls `keyId` — NEXT_PUBLIC_RAZORPAY_KEY_ID, safe to return to the browser. */
    public String getPublicKeyId() {
        return publicKeyId;
    }

    /**
     * Mirrors razorpay.orders.create({ amount, currency, receipt, notes })
     * exactly — same field names, amount already in paise (caller's
     * responsibility, same as the existing route).
     */
    public Order createOrder(long amountPaise, String currency, String receipt, Map<String, String> notes) throws RazorpayException {
        JSONObject request = new JSONObject();
        request.put("amount", amountPaise);
        request.put("currency", currency);
        request.put("receipt", receipt);
        request.put("notes", new JSONObject(notes));
        return client.orders.create(request);
    }

    /** Mirrors razorpay.payments.fetch(paymentId). */
    public Payment fetchPayment(String paymentId) throws RazorpayException {
        return client.payments.fetch(paymentId);
    }

    /**
     * Mirrors verifyRazorpaySignature() in lib/razorpay/server.ts exactly:
     * HMAC-SHA256(secret, "{orderId}|{paymentId}") compared against the
     * client-supplied signature using a constant-time comparison
     * (MessageDigest.isEqual, the JDK's timing-safe byte-array compare —
     * the direct equivalent of Node's crypto.timingSafeEqual).
     */
    public boolean verifySignature(String razorpayOrderId, String razorpayPaymentId, String signatureHex) {
        byte[] provided;
        try {
            provided = hexDecode(signatureHex);
        } catch (IllegalArgumentException malformedHex) {
            // Node's Buffer.from(x, 'hex') silently tolerates malformed hex;
            // here a malformed signature simply can never match the
            // computed HMAC, so treating it as "invalid" (false) rather
            // than throwing reaches the same outcome the existing route
            // does either way — a rejected signature.
            return false;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal((razorpayOrderId + "|" + razorpayPaymentId).getBytes(StandardCharsets.UTF_8));
            if (expected.length != provided.length) {
                return false;
            }
            return java.security.MessageDigest.isEqual(expected, provided);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Unable to compute Razorpay signature", e);
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
