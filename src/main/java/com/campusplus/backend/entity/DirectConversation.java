package com.campusplus.backend.entity;

import com.campusplus.backend.service.messaging.DirectConversationMembers;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "direct_conversations")
public class DirectConversation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lower_user_id", nullable = false, updatable = false) private User lowerUser;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "higher_user_id", nullable = false, updatable = false) private User higherUser;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "last_message_at", nullable = false) private Instant lastMessageAt;
    @Column(name = "last_message_number", nullable = false) private long lastMessageNumber;
    @Version private Long version;

    protected DirectConversation() {}
    public DirectConversation(User first, User second, Instant now) {
        var pair = DirectConversationMembers.between(first.getId(), second.getId());
        lowerUser = first.getId() == pair.lowerUserId() ? first : second;
        higherUser = first.getId() == pair.higherUserId() ? first : second;
        createdAt = now; lastMessageAt = now;
    }
    public long nextMessage(Instant now) {
        lastMessageNumber = Math.incrementExact(lastMessageNumber);
        if (now.isAfter(lastMessageAt)) lastMessageAt = now;
        return lastMessageNumber;
    }
    public DirectConversationMembers members() {
        return new DirectConversationMembers(lowerUser.getId(), higherUser.getId());
    }
    public Long getId() { return id; }
    public long getLastMessageNumber() { return lastMessageNumber; }
    public Instant getLastMessageAt() { return lastMessageAt; }
    @Override public String toString() { return "DirectConversation[REDACTED]"; }
}
