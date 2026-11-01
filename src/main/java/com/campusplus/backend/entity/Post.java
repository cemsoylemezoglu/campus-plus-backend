package com.campusplus.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "posts")
public class Post {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "university_id", nullable = false, updatable = false) private University university;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false) private User createdBy;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16, updatable = false) private PostPublisher publisher;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", updatable = false) private Club club;
    @Column(nullable = false, length = 10000) private String body;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private PostAudience audience;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private PostStatus status = PostStatus.DRAFT;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(name = "removed_at") private Instant removedAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "removed_by") private User removedBy;
    @Column(name = "removal_reason", length = 1000) private String removalReason;
    @Column(name = "moderation_hidden", nullable = false) private boolean moderationHidden;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private Long version;

    protected Post() {}
    public Post(University university, User creator, Club club, String body, PostAudience audience, Instant now) {
        this.university = university; this.createdBy = creator; this.club = club;
        this.publisher = club == null ? PostPublisher.STUDENT : PostPublisher.CLUB;
        this.body = body; this.audience = audience; this.createdAt = now; this.updatedAt = now;
    }
    public void edit(String body, PostAudience audience, Instant now) { this.body = body; this.audience = audience; updatedAt = now; }
    public void publish(Instant now) { status = PostStatus.PUBLISHED; publishedAt = now; updatedAt = now; }
    public void remove(User actor, String reason, Instant now) {
        status = PostStatus.REMOVED; removedBy = actor; removalReason = reason; removedAt = now; updatedAt = now;
    }
    public void moderate(boolean hidden, Instant now) { moderationHidden = hidden; updatedAt = now; }
    public Long getId() { return id; }
    public University getUniversity() { return university; }
    public User getCreatedBy() { return createdBy; }
    public Club getClub() { return club; }
    public PostPublisher getPublisher() { return publisher; }
    public String getBody() { return body; }
    public PostAudience getAudience() { return audience; }
    public PostStatus getStatus() { return status; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getRemovedAt() { return removedAt; }
    public User getRemovedBy() { return removedBy; }
    public String getRemovalReason() { return removalReason; }
    public boolean isModerationHidden() { return moderationHidden; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
