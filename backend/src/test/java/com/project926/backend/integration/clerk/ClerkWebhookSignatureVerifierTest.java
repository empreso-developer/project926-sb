package com.project926.backend.integration.clerk;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The mandatory signature-verification test matrix for the Clerk/Svix
 * webhook (mirrors RazorpayWebhookSignatureVerifierTest's coverage,
 * adapted to Svix's scheme — base64 whsec_ secret, "{id}.{ts}.{body}"
 * signed content, "v1,<sig>" tokens, 5-minute timestamp tolerance).
 */
class ClerkWebhookSignatureVerifierTest {

    private static final String SECRET_KEY_MATERIAL = "test-secret-key-material-0123456789";
    private static final String SECRET = "whsec_" + Base64.getEncoder().encodeToString(SECRET_KEY_MATERIAL.getBytes(StandardCharsets.UTF_8));
    private static final String OTHER_SECRET = "whsec_" + Base64.getEncoder().encodeToString("a-completely-different-secret".getBytes(StandardCharsets.UTF_8));

    private static String sign(String secret, String svixId, String svixTimestamp, byte[] body) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(secret.substring("whsec_".length()));
            String signedContent = svixId + "." + svixTimestamp + ".";
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keyBytes, "HmacSHA256"));
            mac.update(signedContent.getBytes(StandardCharsets.UTF_8));
            byte[] digest = mac.doFinal(body);
            return "v1," + Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String now() {
        return String.valueOf(Instant.now().getEpochSecond());
    }

    private ClerkWebhookSignatureVerifier verifier(String secret) {
        return new ClerkWebhookSignatureVerifier(secret);
    }

    @Test
    void validSignature_correctSecretAndExactBody_isAccepted() {
        byte[] body = "{\"type\":\"user.created\"}".getBytes(StandardCharsets.UTF_8);
        String id = "msg_test123";
        String ts = now();
        String sig = sign(SECRET, id, ts, body);

        assertThat(verifier(SECRET).verify(body, id, ts, sig)).isTrue();
    }

    @Test
    void invalidSignature_garbageBase64_isRejected() {
        byte[] body = "{\"type\":\"user.created\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(verifier(SECRET).verify(body, "msg_x", now(), "v1,AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")).isFalse();
    }

    @Test
    void wrongSecret_correctBodyButDifferentSecretUsedToSign_isRejected() {
        byte[] body = "{\"type\":\"user.created\"}".getBytes(StandardCharsets.UTF_8);
        String id = "msg_x";
        String ts = now();
        String sig = sign(OTHER_SECRET, id, ts, body);

        assertThat(verifier(SECRET).verify(body, id, ts, sig)).isFalse();
    }

    @Test
    void modifiedBody_signatureForBodyA_sentWithBodyB_isRejected() {
        byte[] bodyA = "{\"type\":\"user.created\",\"data\":{\"id\":\"user_a\"}}".getBytes(StandardCharsets.UTF_8);
        byte[] bodyB = "{\"type\":\"user.created\",\"data\":{\"id\":\"user_b\"}}".getBytes(StandardCharsets.UTF_8);
        String id = "msg_x";
        String ts = now();
        String sigForA = sign(SECRET, id, ts, bodyA);

        assertThat(verifier(SECRET).verify(bodyB, id, ts, sigForA)).isFalse();
    }

    @Test
    void modifiedSvixId_invalidatesSignature() {
        byte[] body = "{\"type\":\"user.created\"}".getBytes(StandardCharsets.UTF_8);
        String ts = now();
        String sig = sign(SECRET, "msg_original", ts, body);

        assertThat(verifier(SECRET).verify(body, "msg_tampered", ts, sig)).isFalse();
    }

    @Test
    void missingHeaders_anyOneNull_isRejected() {
        byte[] body = "{\"type\":\"user.created\"}".getBytes(StandardCharsets.UTF_8);
        String id = "msg_x";
        String ts = now();
        String sig = sign(SECRET, id, ts, body);

        assertThat(verifier(SECRET).verify(body, null, ts, sig)).isFalse();
        assertThat(verifier(SECRET).verify(body, id, null, sig)).isFalse();
        assertThat(verifier(SECRET).verify(body, id, ts, null)).isFalse();
    }

    @Test
    void blankHeaders_areRejected() {
        byte[] body = "{\"type\":\"user.created\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(verifier(SECRET).verify(body, "  ", now(), "v1,abc")).isFalse();
        assertThat(verifier(SECRET).verify(body, "msg_x", "  ", "v1,abc")).isFalse();
        assertThat(verifier(SECRET).verify(body, "msg_x", now(), "  ")).isFalse();
    }

    @Test
    void staleTimestamp_outsideToleranceWindow_isRejected() {
        byte[] body = "{\"type\":\"user.created\"}".getBytes(StandardCharsets.UTF_8);
        String id = "msg_x";
        String staleTs = String.valueOf(Instant.now().getEpochSecond() - 600); // 10 minutes ago
        String sig = sign(SECRET, id, staleTs, body);

        assertThat(verifier(SECRET).verify(body, id, staleTs, sig)).isFalse();
    }

    @Test
    void futureTimestamp_outsideToleranceWindow_isRejected() {
        byte[] body = "{\"type\":\"user.created\"}".getBytes(StandardCharsets.UTF_8);
        String id = "msg_x";
        String futureTs = String.valueOf(Instant.now().getEpochSecond() + 600); // 10 minutes ahead
        String sig = sign(SECRET, id, futureTs, body);

        assertThat(verifier(SECRET).verify(body, id, futureTs, sig)).isFalse();
    }

    @Test
    void multipleSignatureTokens_matchOnAnyOneIsAccepted() {
        byte[] body = "{\"type\":\"user.created\"}".getBytes(StandardCharsets.UTF_8);
        String id = "msg_x";
        String ts = now();
        String realSig = sign(SECRET, id, ts, body);
        String header = "v1,bm90dGhlcmVhbHNpZ25hdHVyZQ== " + realSig;

        assertThat(verifier(SECRET).verify(body, id, ts, header)).isTrue();
    }

    @Test
    void rawByteVerification_whitespaceDifferenceInvalidatesSignature() {
        byte[] compact = "{\"type\":\"user.created\"}".getBytes(StandardCharsets.UTF_8);
        byte[] spaced = "{\"type\": \"user.created\"}".getBytes(StandardCharsets.UTF_8);
        String id = "msg_x";
        String ts = now();
        String sigForCompact = sign(SECRET, id, ts, compact);

        assertThat(verifier(SECRET).verify(compact, id, ts, sigForCompact)).isTrue();
        assertThat(verifier(SECRET).verify(spaced, id, ts, sigForCompact)).isFalse();
    }

    @Test
    void unconfiguredSecret_failsClosed() {
        byte[] body = "{\"type\":\"user.created\"}".getBytes(StandardCharsets.UTF_8);
        String id = "msg_x";
        String ts = now();
        String sig = sign(SECRET, id, ts, body);

        assertThat(verifier("").verify(body, id, ts, sig)).isFalse();
        assertThat(verifier(null).verify(body, id, ts, sig)).isFalse();
    }

    @Test
    void secretMissingWhsecPrefix_failsClosed() {
        byte[] body = "{\"type\":\"user.created\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(verifier("not-a-whsec-secret").verify(body, "msg_x", now(), "v1,abc")).isFalse();
    }
}
