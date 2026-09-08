package com.project926.backend.integration.razorpay;

import com.razorpay.RazorpayException;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors verifyRazorpaySignature() in lib/razorpay/server.ts: HMAC-SHA256
 * of "{orderId}|{paymentId}" using the key secret, hex-encoded, compared to
 * the client-supplied signature. Pure logic — no network call, no
 * RazorpayClient construction network I/O (the SDK's constructor just
 * stores credentials, same as the Node SDK).
 */
class RazorpayGatewayTest {

    private static final String SECRET = "test_secret_key_12345";

    private RazorpayGateway gateway() throws RazorpayException {
        return new RazorpayGateway("rzp_test_dummy", SECRET, "rzp_test_dummy");
    }

    private String computeExpectedSignature(String orderId, String paymentId) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] raw = mac.doFinal((orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : raw) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    @Test
    void verifySignature_acceptsCorrectlyComputedSignature() throws Exception {
        String orderId = "order_ABC123";
        String paymentId = "pay_XYZ789";
        String validSignature = computeExpectedSignature(orderId, paymentId);

        assertThat(gateway().verifySignature(orderId, paymentId, validSignature)).isTrue();
    }

    @Test
    void verifySignature_rejectsIncorrectSignature() throws Exception {
        String orderId = "order_ABC123";
        String paymentId = "pay_XYZ789";
        // A validly-formed hex string that is simply wrong.
        String wrongSignature = "0".repeat(64);

        assertThat(gateway().verifySignature(orderId, paymentId, wrongSignature)).isFalse();
    }

    @Test
    void verifySignature_rejectsSignatureComputedForDifferentPaymentId() throws Exception {
        String orderId = "order_ABC123";
        String signatureForDifferentPayment = computeExpectedSignature(orderId, "pay_SOMEONE_ELSE");

        assertThat(gateway().verifySignature(orderId, "pay_XYZ789", signatureForDifferentPayment)).isFalse();
    }

    @Test
    void verifySignature_rejectsMalformedHex_withoutThrowing() throws Exception {
        assertThat(gateway().verifySignature("order_ABC123", "pay_XYZ789", "not-valid-hex!!")).isFalse();
    }

    @Test
    void verifySignature_rejectsEmptySignature() throws Exception {
        assertThat(gateway().verifySignature("order_ABC123", "pay_XYZ789", "")).isFalse();
    }
}
