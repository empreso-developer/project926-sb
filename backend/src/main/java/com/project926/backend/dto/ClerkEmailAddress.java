package com.project926.backend.dto;

/** Mirrors one entry of Clerk's {@code data.email_addresses} array. */
public record ClerkEmailAddress(String id, String emailAddress) {
}
