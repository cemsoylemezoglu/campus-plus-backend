package com.campusplus.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "clubs", uniqueConstraints = @UniqueConstraint(columnNames = {"university_id", "slug"}))
public class Club {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "university_id", nullable = false, updatable = false)
    private University university;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;
    @Column(nullable = false, length = 150) private String name;
    @Column(nullable = false, length = 100, updatable = false) private String slug;
    @Column(nullable = false, length = 5000) private String description;
    @Column(name = "logo_url", length = 500) private String logoUrl;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private ClubStatus status = ClubStatus.PENDING_APPROVAL;
    @Column(name = "review_note", length = 1000) private String reviewNote;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "reviewed_by") private User reviewedBy;
    @Column(name = "reviewed_at") private Instant reviewedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private Long version;

    protected Club() {}
    public Club(University university, User creator, String name, String slug, String description, String logoUrl, Instant now) {
        this.university = university; this.createdBy = creator; this.name = name; this.slug = slug;
        this.description = description; this.logoUrl = logoUrl; this.createdAt = now; this.updatedAt = now;
    }
    public void edit(String name, String description, String logoUrl, Instant now) {
        this.name = name; this.description = description; this.logoUrl = logoUrl; this.updatedAt = now;
    }
    public void transition(ClubStatus status, User reviewer, String reason, Instant now) {
        this.status = status; this.reviewedBy = reviewer; this.reviewNote = reason;
        this.reviewedAt = now; this.updatedAt = now;
    }
    public Long getId() { return id; }
    public University getUniversity() { return university; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getDescription() { return description; }
    public String getLogoUrl() { return logoUrl; }
    public ClubStatus getStatus() { return status; }
    public String getReviewNote() { return reviewNote; }
    public User getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
