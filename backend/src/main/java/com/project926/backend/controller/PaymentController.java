package com.project926.backend.controller;

import com.project926.backend.dto.CreateOrderRequest;
import com.project926.backend.dto.CreateOrderResponse;
import com.project926.backend.dto.VerifyPaymentRequest;
import com.project926.backend.dto.VerifyPaymentResponse;
import com.project926.backend.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mirrors app/(project926)/project926/api/payments/create-order/route.ts
 * and .../payments/verify/route.ts. Both require a valid Clerk JWT (Phase
 * A SecurityConfig's default anyRequest().authenticated() — no permitAll
 * matcher exists for /api/v1/payments/**). The authenticated customer id
 * comes only from jwt.getSubject(), never from the request body.
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/create-order")
    public CreateOrderResponse createOrder(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody CreateOrderRequest request
    ) {
        return paymentService.createOrder(jwt.getSubject(), request);
    }

    @PostMapping("/verify")
    public VerifyPaymentResponse verify(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody VerifyPaymentRequest request
    ) {
        return paymentService.verifyPayment(jwt.getSubject(), request);
    }
}
