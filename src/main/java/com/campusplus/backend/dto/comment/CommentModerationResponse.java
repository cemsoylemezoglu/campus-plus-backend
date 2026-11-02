package com.campusplus.backend.dto.comment;
import com.campusplus.backend.entity.*;
import java.time.Instant;
public record CommentModerationResponse(Long id,Long actorId,CommentModerationScope scope,CommentModerationAction action,
        String reason,Instant createdAt,Long resultingVersion) {
    @Override public String toString(){return "CommentModerationResponse[reason=[REDACTED]]";}
}
