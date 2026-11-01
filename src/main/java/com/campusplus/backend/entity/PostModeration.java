package com.campusplus.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "post_moderation_actions")
public class PostModeration {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false, updatable = false) private Post post;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false, updatable = false) private User actor;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16, updatable = false) private PostModerationAction action;
    @Column(nullable = false, length = 1000, updatable = false) private String reason;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "resulting_version", nullable = false, updatable = false) private Long resultingVersion;

    protected PostModeration() {}
    public PostModeration(Post post, User actor, PostModerationAction action, String reason, Instant now) {
        this.post = post; this.actor = actor; this.action = action; this.reason = reason;
        this.createdAt = now; this.resultingVersion = post.getVersion();
    }
    public Long getId() { return id; }
    public User getActor() { return actor; }
    public PostModerationAction getAction() { return action; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getResultingVersion() { return resultingVersion; }
}
