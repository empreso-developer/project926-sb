package com.project926.backend.integration.resend;

import com.project926.backend.exception.ResendSendException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

/**
 * Mirrors lib/resend/server.ts + the send call in
 * lib/email/send-ticket-confirmation.ts. Calls Resend's HTTP API directly
 * (java.net.http.HttpClient) rather than the official resend-java SDK:
 * neither the current nor latest (4.4.0) version of that SDK exposes
 * content-id on attachments, which is required to reproduce the existing
 * inline CID QR image exactly (Step 9) — see the Phase E report for the
 * full explanation. Resend's REST API itself does support
 * `content_id`, so calling it directly is the only way to preserve the
 * existing behavior faithfully rather than approximating it (e.g. falling
 * back to a data-URL image, which the existing implementation explicitly
 * does not do).
 *
 * Never logs the API key, the Authorization header, or full email HTML —
 * only booking-identifying context the caller supplies for its own logging.
 */
@Component
public class ResendGateway {

    private static final Logger log = LoggerFactory.getLogger(ResendGateway.class);
    private static final URI RESEND_EMAILS_ENDPOINT = URI.create("https://api.resend.com/emails");

    private final HttpClient httpClient;
    private final String apiKey;
    private final String fromAddress;
    private final String replyTo;

    public ResendGateway(
        HttpClient httpClient,
        @Value("${resend.api-key:}") String apiKey,
        @Value("${resend.from-email:Project926 Tickets <onboarding@resend.dev>}") String fromAddress,
        @Value("${resend.reply-to:}") String replyTo
    ) {
        this.httpClient = httpClient;
        this.apiKey = apiKey;
        this.fromAddress = fromAddress;
        this.replyTo = replyTo;
    }

    /**
     * @param attachmentContent   raw bytes (e.g. the PNG QR image) — base64
     *                            encoded here, never as a data: URL, since
     *                            Resend's attachment `content` field expects
     *                            plain base64.
     * @return the Resend-assigned message id, or null if the response
     *     didn't include one (mirrors {@code sendResult?.id ?? null}).
     * @throws ResendSendException mirroring the existing
     *     {@code if (error) throw new Error(error.message || 'Resend API error')}.
     */
    public String sendEmail(
        String to,
        String subject,
        String html,
        byte[] attachmentContent,
        String attachmentFilename,
        String attachmentContentType,
        String attachmentContentId
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResendSendException("RESEND_API_KEY is not configured");
        }

        JSONObject attachment = new JSONObject();
        attachment.put("filename", attachmentFilename);
        attachment.put("content", Base64.getEncoder().encodeToString(attachmentContent));
        attachment.put("content_type", attachmentContentType);
        attachment.put("content_id", attachmentContentId);

        JSONObject body = new JSONObject();
        body.put("from", fromAddress);
        body.put("to", new JSONArray().put(to));
        if (replyTo != null && !replyTo.isBlank()) {
            body.put("reply_to", replyTo);
        }
        body.put("subject", subject);
        body.put("html", html);
        body.put("attachments", new JSONArray().put(attachment));

        HttpRequest request = HttpRequest.newBuilder(RESEND_EMAILS_ENDPOINT)
            .timeout(Duration.ofSeconds(20))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.error("[resend] Network error calling Resend API", e);
            throw new ResendSendException("Could not reach Resend API", e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String errorMessage = extractErrorMessage(response.body());
            log.error("[resend] Resend API returned {}: {}", response.statusCode(), errorMessage);
            throw new ResendSendException(errorMessage != null ? errorMessage : "Resend API error");
        }

        try {
            JSONObject parsed = new JSONObject(response.body());
            return parsed.has("id") ? parsed.getString("id") : null;
        } catch (Exception malformed) {
            log.warn("[resend] Could not parse Resend response body as JSON");
            return null;
        }
    }

    private static String extractErrorMessage(String responseBody) {
        try {
            JSONObject parsed = new JSONObject(responseBody);
            return parsed.optString("message", null);
        } catch (Exception malformed) {
            return responseBody;
        }
    }
}
