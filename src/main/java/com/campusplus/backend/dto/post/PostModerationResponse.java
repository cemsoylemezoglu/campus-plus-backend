package com.campusplus.backend.dto.post;

import com.campusplus.backend.entity.*;
import java.time.Instant;

public record PostModerationResponse(Long id, Long actorId, PostModerationAction action, String reason,
        Instant createdAt, Long resultingVersion) {
    public static PostModerationResponse from(PostModeration m) {
        return new PostModerationResponse(m.getId(), m.getActor().getId(), m.getAction(), m.getReason(), m.getCreatedAt(), m.getResultingVersion());
    }
    @Override public String toString() { return "PostModerationResponse[reason=[REDACTED]]"; }
}
