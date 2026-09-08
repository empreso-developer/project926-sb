package com.project926.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Maps to the existing {@code events} table (see supabase/schema.sql at the
 * repo root — this entity mirrors it, it does not define it).
 *
 * {@code id} is a real {@code uuid} column (unlike {@link Profile#getId()}).
 * {@code organizerId} is a Clerk user id ({@code text}), so stays
 * {@link String} — never UUID, same reasoning as Profile.
 */
@Entity
@Table(name = "events")
public class Event {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organizer_id", nullable = false)
    private String organizerId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "event_time", nullable = false)
    private LocalTime eventTime;

    @Column(name = "venue", nullable = false)
    private String venue;

    @Column(name = "city", nullable = false)
    private String city;

    @Column(name = "banner_url")
    private String bannerUrl;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Event() {
        // JPA
    }

    /**
     * Mirrors createEventAction's insert exactly: id/created_at/updated_at
     * are generated here (not left to a DB default) so the entity is fully
     * populated the moment it's constructed; status is always "draft" for a
     * newly created event, matching the existing action's hardcoded value —
     * callers cannot set any other initial status.
     */
    public static Event createDraft(
        String organizerId,
        String title,
        String description,
        LocalDate eventDate,
        LocalTime eventTime,
        String venue,
        String city,
        String bannerUrl
    ) {
        Event event = new Event();
        event.id = UUID.randomUUID();
        event.organizerId = organizerId;
        event.title = title;
        event.description = description;
        event.eventDate = eventDate;
        event.eventTime = eventTime;
        event.venue = venue;
        event.city = city;
        event.bannerUrl = bannerUrl;
        event.status = "draft";
        OffsetDateTime now = OffsetDateTime.now();
        event.createdAt = now;
        event.updatedAt = now;
        return event;
    }

    /**
     * Mirrors updateEventAction's update set exactly — organizer_id and
     * status are never touched by this method, same as the existing action.
     */
    public void applyOrganizerUpdate(
        String title,
        String description,
        LocalDate eventDate,
        LocalTime eventTime,
        String venue,
        String city,
        String bannerUrl
    ) {
        this.title = title;
        this.description = description;
        this.eventDate = eventDate;
        this.eventTime = eventTime;
        this.venue = venue;
        this.city = city;
        this.bannerUrl = bannerUrl;
    }

    /**
     * Mirrors the status-only updates in publishEventAction /
     * approveEventAction / rejectEventAction: unconditional, no current-
     * status check — matching the existing implementation exactly (no
     * state-machine guard exists there either).
     */
    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public String getOrganizerId() {
        return organizerId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public LocalTime getEventTime() {
        return eventTime;
    }

    public String getVenue() {
        return venue;
    }

    public String getCity() {
        return city;
    }

    public String getBannerUrl() {
        return bannerUrl;
    }

    public String getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
