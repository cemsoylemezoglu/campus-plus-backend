package com.campusplus.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "user_blocks")
public class UserBlock {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocker_id", nullable = false, updatable = false) private User blocker;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocked_id", nullable = false, updatable = false) private User blocked;
    @Column(nullable = false) private boolean active;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private Long version;

    protected UserBlock() {}
    public UserBlock(User blocker, User blocked, Instant now) {
        com.campusplus.backend.service.messaging.DirectConversationMembers.between(blocker.getId(), blocked.getId());
        this.blocker = blocker; this.blocked = blocked; active = true; createdAt = now; updatedAt = now;
    }
    public void setActive(boolean value, Instant now) {
        if (active != value) { active = value; if (now.isAfter(updatedAt)) updatedAt = now; }
    }
    public boolean isActive() { return active; }
    @Override public String toString() { return "UserBlock[REDACTED]"; }
}
