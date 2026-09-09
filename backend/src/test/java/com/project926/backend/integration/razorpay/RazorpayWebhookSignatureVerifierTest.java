package com.project926.backend.integration.razorpay;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase G's mandatory signature-verification test matrix (Step 8/30 of the
 * brief): valid, invalid, missing, wrong-secret, modified-body, empty/
 * malformed-body, and a direct proof the check runs over exact raw bytes
 * rather than any parsed/reformatted representation.
 */
class RazorpayWebhookSignatureVerifierTest {

    private static final String SECRET = "whsec_test_secret_abc123";
    private static final String OTHER_SECRET = "whsec_different_secret_xyz789";

    private static String hmacHex(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(body);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private RazorpayWebhookSignatureVerifier verifier(String secret) {
        return new RazorpayWebhookSignatureVerifier(secret);
    }

    @Test
    void validSignature_correctSecretAndExactBody_isAccepted() {
        byte[] body = "{\"event\":\"payment.captured\",\"payload\":{}}".getBytes(StandardCharsets.UTF_8);
        String signature = hmacHex(SECRET, body);

        assertThat(verifier(SECRET).verify(body, signature)).isTrue();
    }

    @Test
    void invalidSignature_garbageHex_isRejected() {
        byte[] body = "{\"event\":\"payment.captured\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(verifier(SECRET).verify(body, "0000000000000000000000000000000000000000000000000000000000000000")).isFalse();
    }

    @Test
    void wrongSecret_correctBodyButDifferentSecretUsedToSign_isRejected() {
        byte[] body = "{\"event\":\"payment.captured\"}".getBytes(StandardCharsets.UTF_8);
        String signature = hmacHex(OTHER_SECRET, body);

        assertThat(verifier(SECRET).verify(body, signature)).isFalse();
    }

    @Test
    void modifiedBody_signatureForBodyA_sentWithBodyB_isRejected() {
        byte[] bodyA = "{\"event\":\"payment.captured\",\"amount\":50000}".getBytes(StandardCharsets.UTF_8);
        byte[] bodyB = "{\"event\":\"payment.captured\",\"amount\":99999}".getBytes(StandardCharsets.UTF_8);
        String signatureForA = hmacHex(SECRET, bodyA);

        assertThat(verifier(SECRET).verify(bodyB, signatureForA)).isFalse();
    }

    @Test
    void missingSignature_nullHeader_isRejected() {
        byte[] body = "{\"event\":\"payment.captured\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(verifier(SECRET).verify(body, null)).isFalse();
    }

    @Test
    void missingSignature_blankHeader_isRejected() {
        byte[] body = "{\"event\":\"payment.captured\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(verifier(SECRET).verify(body, "   ")).isFalse();
    }

    @Test
    void emptyBody_doesNotBypassVerification() {
        byte[] emptyBody = new byte[0];

        // No signature at all against an empty body: still rejected.
        assertThat(verifier(SECRET).verify(emptyBody, null)).isFalse();
        // A signature computed for non-empty content, sent with an empty
        // body: still rejected (proves the check isn't short-circuiting on
        // an empty payload).
        String signatureForNonEmpty = hmacHex(SECRET, "{\"event\":\"x\"}".getBytes(StandardCharsets.UTF_8));
        assertThat(verifier(SECRET).verify(emptyBody, signatureForNonEmpty)).isFalse();
    }

    @Test
    void malformedBody_nonJsonBytes_signatureStillCheckedFirst() {
        // The verifier operates on raw bytes and has no notion of JSON at
        // all — a non-JSON body with a CORRECTLY computed signature must
        // still be accepted at the signature layer (JSON validity is the
        // controller's separate, later concern).
        byte[] notJson = "this is not json".getBytes(StandardCharsets.UTF_8);
        String signature = hmacHex(SECRET, notJson);

        assertThat(verifier(SECRET).verify(notJson, signature)).isTrue();
    }

    @Test
    void rawByteVerification_whitespaceDifferenceInvalidatesSignature() {
        // Direct proof (Step 8's "whitespace preservation" requirement)
        // that verification uses the exact raw bytes, not a parsed-then-
        // reformatted JSON representation: two byte sequences that would
        // parse to an IDENTICAL JSON object, differing only in whitespace,
        // produce DIFFERENT valid signatures — a signature computed for
        // one must not validate the other.
        byte[] compact = "{\"event\":\"payment.captured\"}".getBytes(StandardCharsets.UTF_8);
        byte[] spaced = "{\"event\": \"payment.captured\"}".getBytes(StandardCharsets.UTF_8);
        String signatureForCompact = hmacHex(SECRET, compact);

        assertThat(verifier(SECRET).verify(compact, signatureForCompact)).isTrue();
        assertThat(verifier(SECRET).verify(spaced, signatureForCompact)).isFalse();
    }

    @Test
    void unconfiguredSecret_failsClosed() {
        byte[] body = "{\"event\":\"payment.captured\"}".getBytes(StandardCharsets.UTF_8);
        String signature = hmacHex(SECRET, body);

        assertThat(verifier("").verify(body, signature)).isFalse();
        assertThat(verifier(null).verify(body, signature)).isFalse();
    }
}
