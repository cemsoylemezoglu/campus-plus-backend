package com.campusplus.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "direct_conversation_states")
public class DirectConversationState {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false, updatable = false) private DirectConversation conversation;
    @Column(name = "lower_user_id", nullable = false, updatable = false) private long lowerUserId;
    @Column(name = "higher_user_id", nullable = false, updatable = false) private long higherUserId;
    @Column(name = "user_id", nullable = false, updatable = false) private long userId;
    @Column(name = "last_read_message_number", nullable = false) private long lastReadMessageNumber;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private Long version;

    protected DirectConversationState() {}
    public DirectConversationState(DirectConversation conversation, long userId, Instant now) {
        if (!conversation.members().contains(userId)) throw new IllegalArgumentException("Participant required");
        this.conversation = conversation; this.userId = userId; this.updatedAt = now;
        lowerUserId = conversation.members().lowerUserId(); higherUserId = conversation.members().higherUserId();
    }
    public void markRead(long number, Instant now) {
        if (number <= 0 || number > conversation.getLastMessageNumber())
            throw new IllegalArgumentException("Invalid read position");
        if (number > lastReadMessageNumber) {
            lastReadMessageNumber = number; if (now.isAfter(updatedAt)) updatedAt = now;
        }
    }
    public long getLastReadMessageNumber() { return lastReadMessageNumber; }
    @Override public String toString() { return "DirectConversationState[REDACTED]"; }
}
