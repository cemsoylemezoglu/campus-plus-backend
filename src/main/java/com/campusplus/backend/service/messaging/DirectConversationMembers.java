package com.campusplus.backend.service.messaging;

/** Canonical pair; the database will enforce the same ordering and uniqueness. */
public record DirectConversationMembers(long lowerUserId, long higherUserId) {
    public DirectConversationMembers {
        if (lowerUserId <= 0 || higherUserId <= lowerUserId) {
            throw new IllegalArgumentException("Two distinct, ordered user IDs are required");
        }
    }

    public static DirectConversationMembers between(long firstUserId, long secondUserId) {
        return new DirectConversationMembers(Math.min(firstUserId, secondUserId),
                Math.max(firstUserId, secondUserId));
    }

    public boolean contains(Long userId) {
        return userId != null && (userId == lowerUserId || userId == higherUserId);
    }
}
