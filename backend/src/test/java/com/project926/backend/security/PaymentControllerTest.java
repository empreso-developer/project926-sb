package com.project926.backend.security;

import com.project926.backend.config.SecurityConfig;
import com.project926.backend.controller.PaymentController;
import com.project926.backend.dto.CreateOrderResponse;
import com.project926.backend.dto.VerifyPaymentResponse;
import com.project926.backend.exception.BookingValidationException;
import com.project926.backend.exception.ForbiddenException;
import com.project926.backend.exception.GlobalExceptionHandler;
import com.project926.backend.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers Step 19's security requirements at the HTTP layer for the payment
 * endpoints: unauthenticated -> 401, the Clerk identity always comes from
 * the JWT subject (never the body), and error mapping.
 */
@WebMvcTest(controllers = PaymentController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    private static final String CALLER_ID = "user_caller00000000000000";

    @Test
    void createOrder_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/payments/create-order")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateOrderJson()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void verify_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/payments/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validVerifyJson()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void createOrder_usesJwtSubjectAsCustomerId() throws Exception {
        UUID bookingId = UUID.randomUUID();
        when(paymentService.createOrder(eq(CALLER_ID), any()))
            .thenReturn(new CreateOrderResponse("order_x", bookingId, 10000L, "INR", "rzp_test_x"));

        mockMvc.perform(post("/api/v1/payments/create-order")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(CALLER_ID).claim(SUB, CALLER_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateOrderJson()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderId").value("order_x"))
            .andExpect(jsonPath("$.keyId").value("rzp_test_x"));

        verify(paymentService).createOrder(eq(CALLER_ID), any());
    }

    @Test
    void createOrder_responseFieldsAreCamelCase_notSnakeCase() throws Exception {
        // Guards against the global SNAKE_CASE Jackson strategy silently
        // breaking this endpoint's existing camelCase contract (Step 20).
        UUID bookingId = UUID.randomUUID();
        when(paymentService.createOrder(eq(CALLER_ID), any()))
            .thenReturn(new CreateOrderResponse("order_x", bookingId, 10000L, "INR", "rzp_test_x"));

        mockMvc.perform(post("/api/v1/payments/create-order")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(CALLER_ID).claim(SUB, CALLER_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateOrderJson()))
            .andExpect(jsonPath("$.orderId").exists())
            .andExpect(jsonPath("$.bookingId").exists())
            .andExpect(jsonPath("$.order_id").doesNotExist())
            .andExpect(jsonPath("$.booking_id").doesNotExist());
    }

    @Test
    void createOrder_invalidBody_returns400() throws Exception {
        String invalidBody = """
            {"eventId":"not-a-uuid","items":[]}
            """;

        mockMvc.perform(post("/api/v1/payments/create-order")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(CALLER_ID).claim(SUB, CALLER_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidBody))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createOrder_businessValidationError_preservesDynamicMessage() throws Exception {
        when(paymentService.createOrder(eq(CALLER_ID), any()))
            .thenThrow(new BookingValidationException("Only 2 tickets left for VIP"));

        mockMvc.perform(post("/api/v1/payments/create-order")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(CALLER_ID).claim(SUB, CALLER_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateOrderJson()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Only 2 tickets left for VIP"));
    }

    @Test
    void verify_usesJwtSubjectAsCustomerId() throws Exception {
        UUID bookingId = UUID.randomUUID();
        when(paymentService.verifyPayment(eq(CALLER_ID), any()))
            .thenReturn(new VerifyPaymentResponse(true, bookingId, "BK-1", null));

        mockMvc.perform(post("/api/v1/payments/verify")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(CALLER_ID).claim(SUB, CALLER_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validVerifyJson()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.reference").value("BK-1"));

        verify(paymentService).verifyPayment(eq(CALLER_ID), any());
    }

    @Test
    void verify_notOwnedByCaller_returns403() throws Exception {
        when(paymentService.verifyPayment(eq(CALLER_ID), any()))
            .thenThrow(new ForbiddenException("Forbidden"));

        mockMvc.perform(post("/api/v1/payments/verify")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(CALLER_ID).claim(SUB, CALLER_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validVerifyJson()))
            .andExpect(status().isForbidden());
    }

    private String validCreateOrderJson() {
        return """
            {"eventId":"%s","items":[{"ticketTypeId":"%s","quantity":2}]}
            """.formatted(UUID.randomUUID(), UUID.randomUUID());
    }

    private String validVerifyJson() {
        return """
            {"razorpayOrderId":"order_x","razorpayPaymentId":"pay_x","razorpaySignature":"sig_x","bookingId":"%s"}
            """.formatted(UUID.randomUUID());
    }
}
