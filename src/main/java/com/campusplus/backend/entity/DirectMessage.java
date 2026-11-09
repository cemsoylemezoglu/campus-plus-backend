package com.campusplus.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "direct_messages")
public class DirectMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false, updatable = false) private DirectConversation conversation;
    @Column(name = "lower_user_id", nullable = false, updatable = false) private long lowerUserId;
    @Column(name = "higher_user_id", nullable = false, updatable = false) private long higherUserId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false, updatable = false) private User sender;
    @Column(name = "message_number", nullable = false, updatable = false) private long messageNumber;
    @Column(name = "client_message_id", nullable = false, updatable = false) private UUID clientMessageId;
    @Column(nullable = false, length = 4000, updatable = false) private String body;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected DirectMessage() {}
    public DirectMessage(DirectConversation conversation, User sender, UUID key, String body, Instant now) {
        if (!conversation.members().contains(sender.getId()) || key == null || body == null
                || body.isBlank() || body.length() > 4000 || body.indexOf(0) >= 0) {
            throw new IllegalArgumentException("Invalid direct message");
        }
        this.conversation = conversation; this.sender = sender; this.clientMessageId = key;
        this.body = body; this.createdAt = now;
        lowerUserId = conversation.members().lowerUserId(); higherUserId = conversation.members().higherUserId();
        messageNumber = conversation.nextMessage(now);
    }
    public Long getId() { return id; }
    public Long getConversationId() { return conversation.getId(); }
    public Long getSenderId() { return sender.getId(); }
    public long getMessageNumber() { return messageNumber; }
    public UUID getClientMessageId() { return clientMessageId; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
    @Override public String toString() { return "DirectMessage[content=[REDACTED]]"; }
}
