package com.project926.backend.security;

import com.project926.backend.config.SecurityConfig;
import com.project926.backend.controller.ClerkWebhookController;
import com.project926.backend.exception.GlobalExceptionHandler;
import com.project926.backend.integration.clerk.ClerkWebhookSignatureVerifier;
import com.project926.backend.service.ClerkWebhookService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the Clerk webhook endpoint's security/HTTP-contract requirements
 * — mirrors RazorpayWebhookControllerTest's structure exactly: reachable
 * WITHOUT a Clerk JWT (proving SecurityConfig's narrow permitAll), and
 * response codes following the same 400/200/500 rules. HMAC math is
 * unit-tested independently in ClerkWebhookSignatureVerifierTest — here
 * the verifier is mocked so this class tests only the controller's own
 * decisions.
 */
@WebMvcTest(controllers = ClerkWebhookController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class ClerkWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClerkWebhookSignatureVerifier signatureVerifier;

    @MockBean
    private ClerkWebhookService webhookService;

    private static final String VALID_CREATED_BODY = """
        {"type":"user.created","data":{"id":"user_x","email_addresses":[{"id":"e1","email_address":"a@example.com"}],"primary_email_address_id":"e1","first_name":"A","last_name":"B","public_metadata":{},"unsafe_metadata":{}}}
        """;

    // ---- no Clerk auth required (public endpoint) --------------------------

    @Test
    void webhook_reachableWithoutAnyAuthorizationHeader_neverReturns401() throws Exception {
        when(signatureVerifier.verify(any(), any(), any(), any())).thenReturn(true);

        mockMvc.perform(post("/api/v1/webhooks/clerk")
                .header("svix-id", "msg_1")
                .header("svix-timestamp", "1700000000")
                .header("svix-signature", "v1,abc")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CREATED_BODY))
            .andExpect(status().isOk());
    }

    // ---- signature verification ---------------------------------------------

    @Test
    void invalidSignature_returns400_neverReachesService() throws Exception {
        when(signatureVerifier.verify(any(), any(), any(), eq("v1,bad"))).thenReturn(false);

        mockMvc.perform(post("/api/v1/webhooks/clerk")
                .header("svix-id", "msg_1")
                .header("svix-timestamp", "1700000000")
                .header("svix-signature", "v1,bad")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CREATED_BODY))
            .andExpect(status().isBadRequest());

        verify(webhookService, never()).process(any(), any());
    }

    @Test
    void missingSignatureHeaders_verifierCalledWithNulls_rejectedAs400() throws Exception {
        when(signatureVerifier.verify(any(), isNull(), isNull(), isNull())).thenReturn(false);

        mockMvc.perform(post("/api/v1/webhooks/clerk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CREATED_BODY))
            .andExpect(status().isBadRequest());

        verify(signatureVerifier).verify(any(), isNull(), isNull(), isNull());
    }

    @Test
    void signatureVerifiedOverExactRawBytesAndHeaders_matchingTheHttpRequest() throws Exception {
        when(signatureVerifier.verify(any(), any(), any(), any())).thenReturn(true);

        mockMvc.perform(post("/api/v1/webhooks/clerk")
                .header("svix-id", "msg_42")
                .header("svix-timestamp", "1700000042")
                .header("svix-signature", "v1,sigvalue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CREATED_BODY))
            .andExpect(status().isOk());

        verify(signatureVerifier).verify(
            eq(VALID_CREATED_BODY.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            eq("msg_42"), eq("1700000042"), eq("v1,sigvalue"));
    }

    // ---- malformed / incomplete body after valid signature -------------------

    @Test
    void validSignature_malformedJson_returns400() throws Exception {
        when(signatureVerifier.verify(any(), any(), any(), any())).thenReturn(true);

        mockMvc.perform(post("/api/v1/webhooks/clerk")
                .header("svix-id", "msg_1")
                .header("svix-timestamp", "1700000000")
                .header("svix-signature", "v1,abc")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not valid json"))
            .andExpect(status().isBadRequest());

        verify(webhookService, never()).process(any(), any());
    }

    @Test
    void validSignature_missingTypeOrDataId_returns400() throws Exception {
        when(signatureVerifier.verify(any(), any(), any(), any())).thenReturn(true);

        mockMvc.perform(post("/api/v1/webhooks/clerk")
                .header("svix-id", "msg_1")
                .header("svix-timestamp", "1700000000")
                .header("svix-signature", "v1,abc")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"user.created","data":{}}
                    """))
            .andExpect(status().isBadRequest());

        verify(webhookService, never()).process(any(), any());
    }

    // ---- successful outcomes -> 200 ------------------------------------------

    @Test
    void validSignatureAndBody_delegatesToServiceAndReturns200() throws Exception {
        when(signatureVerifier.verify(any(), any(), any(), any())).thenReturn(true);

        mockMvc.perform(post("/api/v1/webhooks/clerk")
                .header("svix-id", "msg_1")
                .header("svix-timestamp", "1700000000")
                .header("svix-signature", "v1,abc")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CREATED_BODY))
            .andExpect(status().isOk());

        verify(webhookService).process(eq("user.created"), any());
    }

    @Test
    void unsupportedEventType_stillReturns200_serviceIgnoresIt() throws Exception {
        when(signatureVerifier.verify(any(), any(), any(), any())).thenReturn(true);

        mockMvc.perform(post("/api/v1/webhooks/clerk")
                .header("svix-id", "msg_1")
                .header("svix-timestamp", "1700000000")
                .header("svix-signature", "v1,abc")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"session.created","data":{"id":"sess_x"}}
                    """))
            .andExpect(status().isOk());

        verify(webhookService).process(eq("session.created"), any());
    }

    // ---- internal/transient failure -> 500 -----------------------------------

    @Test
    void serviceThrowsUnexpectedException_returns500() throws Exception {
        when(signatureVerifier.verify(any(), any(), any(), any())).thenReturn(true);
        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
            .when(webhookService).process(any(), any());

        mockMvc.perform(post("/api/v1/webhooks/clerk")
                .header("svix-id", "msg_1")
                .header("svix-timestamp", "1700000000")
                .header("svix-signature", "v1,abc")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CREATED_BODY))
            .andExpect(status().isInternalServerError());
    }
}
