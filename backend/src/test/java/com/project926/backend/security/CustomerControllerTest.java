package com.project926.backend.security;

import com.project926.backend.config.SecurityConfig;
import com.project926.backend.controller.CustomerController;
import com.project926.backend.dto.CustomerBookingDto;
import com.project926.backend.service.CustomerBookingService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers GET /api/v1/customer/bookings' security requirements: 401 with no
 * token, and — critically — that the customer id used to fetch bookings
 * comes ONLY from the validated JWT subject, never from any client-
 * suppliable parameter, so one customer can never retrieve another's
 * bookings through this endpoint.
 */
@WebMvcTest(controllers = CustomerController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CustomerBookingService customerBookingService;

    @Test
    void bookingsWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/customer/bookings"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void bookingsWithMalformedTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/customer/bookings").header("Authorization", "Bearer not-a-real-jwt"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedCustomer_retrievesOwnBookings_usingJwtSubjectOnly() throws Exception {
        String clerkUserId = "user_3GjubwvONQwJQcpQRdNtbEUEbP5";
        when(customerBookingService.listOwnBookings(clerkUserId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/customer/bookings")
                .with(SecurityMockMvcRequestPostProcessors.jwt()
                    .jwt(jwt -> jwt.subject(clerkUserId).claim(SUB, clerkUserId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());

        ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
        verify(customerBookingService).listOwnBookings(idCaptor.capture());
        assertThat(idCaptor.getValue()).isEqualTo(clerkUserId);
    }

    @Test
    void cannotRequestAnotherCustomersBookings_endpointHasNoCustomerIdParameterAtAll() throws Exception {
        String realCallerId = "user_realCaller0000000000000";
        String someoneElseId = "user_someoneElse00000000000";
        when(customerBookingService.listOwnBookings(anyString())).thenReturn(List.of());

        // There is no customer_id/userId query or path parameter on this
        // endpoint at all — attempting to supply one is simply ignored,
        // since the controller never reads it; only the JWT subject can
        // ever determine whose bookings are returned.
        mockMvc.perform(get("/api/v1/customer/bookings?customer_id=" + someoneElseId + "&userId=" + someoneElseId)
                .with(SecurityMockMvcRequestPostProcessors.jwt()
                    .jwt(jwt -> jwt.subject(realCallerId).claim(SUB, realCallerId))))
            .andExpect(status().isOk());

        verify(customerBookingService).listOwnBookings(realCallerId);
    }

    @Test
    void returnsServiceResultVerbatim() throws Exception {
        String clerkUserId = "user_hasBookings000000000000";
        var dto = new CustomerBookingDto(
            java.util.UUID.randomUUID(), "BK-ABCD1234", "confirmed",
            new java.math.BigDecimal("500.00"), "data:image/png;base64,xxx", null,
            java.time.OffsetDateTime.now(), 2, null, null);
        when(customerBookingService.listOwnBookings(clerkUserId)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/customer/bookings")
                .with(SecurityMockMvcRequestPostProcessors.jwt()
                    .jwt(jwt -> jwt.subject(clerkUserId).claim(SUB, clerkUserId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].reference").value("BK-ABCD1234"))
            .andExpect(jsonPath("$[0].status").value("confirmed"))
            .andExpect(jsonPath("$[0].ticket_quantity").value(2));
    }
}
