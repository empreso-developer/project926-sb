package com.project926.backend.controller;

import com.project926.backend.dto.CustomerBookingDto;
import com.project926.backend.service.CustomerBookingService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Mirrors app/(project926)/p/dashboard/customer/page.tsx's own-bookings
 * read (CustomerBookingService). Requires a valid Clerk JWT (Phase A
 * SecurityConfig's default anyRequest().authenticated() — no permitAll
 * matcher exists for /api/v1/customer/**). The authenticated customer id
 * comes only from jwt.getSubject(), NEVER from a path/query/body
 * parameter — there is no way for a caller to request another customer's
 * bookings through this endpoint.
 */
@RestController
@RequestMapping("/api/v1/customer")
public class CustomerController {

    private final CustomerBookingService customerBookingService;

    public CustomerController(CustomerBookingService customerBookingService) {
        this.customerBookingService = customerBookingService;
    }

    @GetMapping("/bookings")
    public List<CustomerBookingDto> listOwnBookings(@AuthenticationPrincipal Jwt jwt) {
        return customerBookingService.listOwnBookings(jwt.getSubject());
    }
}
