package com.project926.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Maps to the existing {@code profiles} table (see supabase/schema.sql at
 * the repo root — this entity does not define the schema, it mirrors it).
 *
 * {@code id} is a Clerk user id (e.g. "user_3GjubwvONQwJQcpQRdNtbEUEbP5"),
 * not a UUID — the column was deliberately retyped from uuid to text in
 * migration 20260720100000_fix_clerk_id_type_mismatch.sql because Clerk ids
 * are never valid UUIDs. It must stay {@link String} here; never UUID.
 */
@Entity
@Table(name = "profiles")
public class Profile {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Profile() {
        // JPA
    }

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getRole() {
        return role;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
