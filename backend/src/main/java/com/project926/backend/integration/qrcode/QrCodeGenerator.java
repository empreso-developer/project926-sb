package com.project926.backend.integration.qrcode;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Mirrors the existing verify route's QR generation exactly:
 * <pre>
 *   const qrPayload = JSON.stringify({ ref: booking.reference, booking_id: booking.id, event_id: booking.event_id });
 *   const qrDataUrl = await QRCode.toDataURL(qrPayload, { errorCorrectionLevel: 'M', margin: 1, width: 320 });
 * </pre>
 * Same JSON field names/values, same error-correction level (M), same
 * margin (1 module) and width (320px), same output shape (a
 * "data:image/png;base64,..." data URL — not raw bytes, not just the
 * payload). Built with ZXing (the Java QR library; the Node `qrcode`
 * package has no direct Java port) rather than reproducing any encoding
 * math by hand — only the observable output (payload content, format,
 * dimensions) is matched, not byte-for-byte PNG output, since two
 * different QR libraries will legitimately produce different pixel data
 * for the same logical QR code.
 *
 * IMPORTANT: this does not decide *when* to persist the result — that is
 * PaymentService's responsibility, calling this before invoking
 * confirm_booking_and_commit_inventory, exactly matching the existing
 * route's sequence (QR generated, then passed as p_qr_code to the RPC).
 */
@Component
public class QrCodeGenerator {

    private static final int WIDTH = 320;
    private static final int MARGIN = 1;

    /**
     * @param reference the booking reference (e.g. "BK-XXXXXXXX-YYMMDD")
     * @param bookingId the booking's UUID
     * @param eventId   the event's UUID — NOT trusted by any future check-in
     *                  logic (that's Phase F's concern), included here only
     *                  because the existing payload includes it.
     * @return a "data:image/png;base64,..." data URL, exactly matching the
     *     existing bookings.qr_code column's stored format.
     */
    public String generateDataUrl(String reference, UUID bookingId, UUID eventId) {
        JSONObject payload = new JSONObject();
        payload.put("ref", reference);
        payload.put("booking_id", bookingId.toString());
        payload.put("event_id", eventId.toString());
        String qrPayload = payload.toString();

        try {
            Map<EncodeHintType, Object> hints = Map.of(
                EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN, MARGIN
            );
            BitMatrix matrix = new QRCodeWriter().encode(qrPayload, BarcodeFormat.QR_CODE, WIDTH, WIDTH, hints);

            ByteArrayOutputStream pngBytes = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", pngBytes);

            String base64 = Base64.getEncoder().encodeToString(pngBytes.toByteArray());
            return "data:image/png;base64," + base64;
        } catch (WriterException | IOException e) {
            throw new IllegalStateException("Failed to generate ticket QR code", e);
        }
    }
}
