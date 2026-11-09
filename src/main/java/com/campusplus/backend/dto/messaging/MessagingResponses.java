package com.campusplus.backend.dto.messaging;

import com.campusplus.backend.entity.DirectMessage;
import java.time.Instant;
import java.util.List;

public final class MessagingResponses {
    private MessagingResponses() {}
    public record Message(Long id, Long conversationId, long senderId, long messageNumber, String body, Instant createdAt) {
        public static Message from(DirectMessage message) {
            return new Message(message.getId(), message.getConversationId(), message.getSenderId(),
                    message.getMessageNumber(), message.getBody(), message.getCreatedAt());
        }
        @Override public String toString() { return "Message[content=[REDACTED]]"; }
    }
    public record Conversation(long id, long otherUserId, Message lastMessage, long unreadCount, Instant lastMessageAt) {
        @Override public String toString() { return "Conversation[content=[REDACTED]]"; }
    }
    public record CursorPage<T>(List<T> content, String nextCursor) {
        public CursorPage { content = List.copyOf(content); }
        @Override public String toString() { return "CursorPage[content=[REDACTED]]"; }
    }
    public record ReadState(long conversationId, long throughMessageNumber) {}
    public record UnreadCount(long count) {}
    public record Block(long userId, Instant blockedAt) {}
    public record BlockPage(List<Block> content, Long nextBeforeUserId) {
        public BlockPage { content = List.copyOf(content); }
        @Override public String toString() { return "BlockPage[content=[REDACTED]]"; }
    }
}
