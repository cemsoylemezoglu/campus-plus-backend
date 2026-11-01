package com.campusplus.backend.dto.post;

import com.campusplus.backend.entity.*;
import java.time.Instant;

public record ManagedPostResponse(PostResponse content, PostStatus status, PostAudience audience,
        boolean moderationHidden, Instant createdAt, Instant removedAt, String removalReason) {
    public static ManagedPostResponse from(Post p) {
        return new ManagedPostResponse(PostResponse.from(p), p.getStatus(), p.getAudience(), p.isModerationHidden(),
                p.getCreatedAt(), p.getRemovedAt(), p.getRemovalReason());
    }
    @Override public String toString() { return "ManagedPostResponse[content=[REDACTED]]"; }
}
