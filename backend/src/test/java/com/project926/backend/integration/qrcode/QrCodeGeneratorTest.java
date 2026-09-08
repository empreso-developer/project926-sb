package com.project926.backend.integration.qrcode;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the generated QR is genuinely valid and correctly encodes the
 * existing payload shape (Step 18) — not merely that bytes were produced.
 * Generates, then independently DECODES the QR with ZXing's own reader
 * (a separate code path from the writer) and asserts the decoded JSON
 * exactly matches {ref, booking_id, event_id}, mirroring the existing
 * verify route's {@code JSON.stringify({ ref: booking.reference, booking_id: booking.id, event_id: booking.event_id })}.
 */
class QrCodeGeneratorTest {

    private final QrCodeGenerator generator = new QrCodeGenerator();

    @Test
    void generateDataUrl_producesDataUrlWithPngMimeType() {
        String reference = "BK-ABCD1234-260101";
        UUID bookingId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        String dataUrl = generator.generateDataUrl(reference, bookingId, eventId);

        assertThat(dataUrl).startsWith("data:image/png;base64,");
    }

    @Test
    void generateDataUrl_decodesToExactExpectedPayload() throws Exception {
        String reference = "BK-XYZW9876-260615";
        UUID bookingId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        String dataUrl = generator.generateDataUrl(reference, bookingId, eventId);
        String decodedText = decodeQr(dataUrl);

        JSONObject payload = new JSONObject(decodedText);
        assertThat(payload.keySet()).containsExactlyInAnyOrder("ref", "booking_id", "event_id");
        assertThat(payload.getString("ref")).isEqualTo(reference);
        assertThat(payload.getString("booking_id")).isEqualTo(bookingId.toString());
        assertThat(payload.getString("event_id")).isEqualTo(eventId.toString());
    }

    @Test
    void generateDataUrl_isDeterministic_sameInputsProduceSameDecodedPayload() throws Exception {
        // Mirrors the existing route's comment: "pure function of booking
        // reference/id/event_id -- deterministic, so it's identical however
        // many times this runs" — proven here at the decoded-payload level
        // (not byte-for-byte PNG, since that's an implementation detail of
        // the QR library, not an observable behavior).
        String reference = "BK-SAME0000-260101";
        UUID bookingId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        String first = decodeQr(generator.generateDataUrl(reference, bookingId, eventId));
        String second = decodeQr(generator.generateDataUrl(reference, bookingId, eventId));

        assertThat(new JSONObject(first).toMap()).isEqualTo(new JSONObject(second).toMap());
    }

    @Test
    void generateDataUrl_differentBookings_produceDifferentPayloads() throws Exception {
        String decodedA = decodeQr(generator.generateDataUrl("BK-AAAA0000-260101", UUID.randomUUID(), UUID.randomUUID()));
        String decodedB = decodeQr(generator.generateDataUrl("BK-BBBB0000-260101", UUID.randomUUID(), UUID.randomUUID()));

        assertThat(decodedA).isNotEqualTo(decodedB);
    }

    private static String decodeQr(String dataUrl) throws Exception {
        String base64 = dataUrl.substring(dataUrl.indexOf(',') + 1);
        byte[] pngBytes = Base64.getDecoder().decode(base64);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(pngBytes));

        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        // TRY_HARDER: without it, ZXing's reader occasionally fails to
        // locate/decode a freshly-rendered (not camera-captured) QR image
        // for certain random payload lengths — a decoder-robustness
        // setting, not a change to what QrCodeGenerator itself produces.
        Map<DecodeHintType, Object> hints = Map.of(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        Result result = new MultiFormatReader().decode(bitmap, hints);
        return result.getText();
    }
}
