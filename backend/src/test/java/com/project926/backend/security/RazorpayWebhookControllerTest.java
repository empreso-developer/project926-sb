package com.project926.backend.security;

import com.project926.backend.config.SecurityConfig;
import com.project926.backend.controller.RazorpayWebhookController;
import com.project926.backend.exception.GlobalExceptionHandler;
import com.project926.backend.integration.razorpay.RazorpayWebhookSignatureVerifier;
import com.project926.backend.service.RazorpayWebhookService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers Phase G's security/HTTP-contract requirements for the webhook
 * endpoint: it must be reachable WITHOUT a Clerk JWT (proving
 * SecurityConfig's narrow permitAll), and its response codes must follow
 * Section 24's rules. HMAC math itself is unit-tested independently in
 * RazorpayWebhookSignatureVerifierTest — here the verifier is mocked so
 * this class can test the controller's OWN decisions (parsing, shape
 * validation, status-code selection) in isolation.
 */
@WebMvcTest(controllers = RazorpayWebhookController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class RazorpayWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RazorpayWebhookSignatureVerifier signatureVerifier;

    @MockBean
    private RazorpayWebhookService webhookService;

    private static final String VALID_CAPTURED_BODY = """
        {"event":"payment.captured","payload":{"payment":{"entity":{"id":"pay_x","order_id":"order_x","amount":50000,"currency":"INR","status":"captured"}}}}
        """;

    // ---- no Clerk auth required (public endpoint) --------------------------

    @Test
    void webhook_reachableWithoutAnyAuthorizationHeader_neverReturns401() throws Exception {
        when(signatureVerifier.verify(any(), any())).thenReturn(true);

        mockMvc.perform(post("/api/v1/webhooks/razorpay")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CAPTURED_BODY))
            .andExpect(status().isOk());
    }

    // ---- signature verification ---------------------------------------------

    @Test
    void invalidSignature_returns400_neverReachesService() throws Exception {
        when(signatureVerifier.verify(any(), eq("bad_signature"))).thenReturn(false);

        mockMvc.perform(post("/api/v1/webhooks/razorpay")
                .header("X-Razorpay-Signature", "bad_signature")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CAPTURED_BODY))
            .andExpect(status().isBadRequest());

        verify(webhookService, org.mockito.Mockito.never()).process(any(), any());
    }

    @Test
    void missingSignatureHeader_verifierCalledWithNull_rejectedAs400() throws Exception {
        when(signatureVerifier.verify(any(), isNull())).thenReturn(false);

        mockMvc.perform(post("/api/v1/webhooks/razorpay")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CAPTURED_BODY))
            .andExpect(status().isBadRequest());

        verify(signatureVerifier).verify(any(), isNull());
    }

    @Test
    void signatureVerifiedOverExactRawBytes_matchingTheHttpBody() throws Exception {
        when(signatureVerifier.verify(any(), any())).thenReturn(true);

        mockMvc.perform(post("/api/v1/webhooks/razorpay")
                .header("X-Razorpay-Signature", "sig123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CAPTURED_BODY))
            .andExpect(status().isOk());

        verify(signatureVerifier).verify(eq(VALID_CAPTURED_BODY.getBytes(java.nio.charset.StandardCharsets.UTF_8)), eq("sig123"));
    }

    // ---- malformed / incomplete body after valid signature -------------------

    @Test
    void validSignature_malformedJson_returns400() throws Exception {
        when(signatureVerifier.verify(any(), any())).thenReturn(true);

        mockMvc.perform(post("/api/v1/webhooks/razorpay")
                .header("X-Razorpay-Signature", "sig123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not valid json"))
            .andExpect(status().isBadRequest());

        verify(webhookService, org.mockito.Mockito.never()).process(any(), any());
    }

    @Test
    void validSignature_missingPaymentEntity_returns400() throws Exception {
        when(signatureVerifier.verify(any(), any())).thenReturn(true);

        mockMvc.perform(post("/api/v1/webhooks/razorpay")
                .header("X-Razorpay-Signature", "sig123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"event":"payment.captured","payload":{}}
                    """))
            .andExpect(status().isBadRequest());

        verify(webhookService, org.mockito.Mockito.never()).process(any(), any());
    }

    // ---- successful / business-state outcomes -> 200 -------------------------

    @Test
    void validSignatureAndBody_delegatesToServiceAndReturns200() throws Exception {
        when(signatureVerifier.verify(any(), any())).thenReturn(true);

        mockMvc.perform(post("/api/v1/webhooks/razorpay")
                .header("X-Razorpay-Signature", "sig123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CAPTURED_BODY))
            .andExpect(status().isOk());

        verify(webhookService).process(eq("payment.captured"), any());
    }

    // ---- internal/transient failure -> 500 (worth retrying) ------------------

    @Test
    void serviceThrowsUnexpectedException_returns500() throws Exception {
        when(signatureVerifier.verify(any(), any())).thenReturn(true);
        org.mockito.Mockito.doThrow(new com.project926.backend.exception.BookingConfirmationException("boom"))
            .when(webhookService).process(any(), any());

        mockMvc.perform(post("/api/v1/webhooks/razorpay")
                .header("X-Razorpay-Signature", "sig123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CAPTURED_BODY))
            .andExpect(status().isInternalServerError());
    }
}
