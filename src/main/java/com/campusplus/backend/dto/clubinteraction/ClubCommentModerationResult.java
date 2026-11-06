package com.campusplus.backend.dto.clubinteraction;
import com.campusplus.backend.entity.*;
/** Local managers never receive hidden text or the author's identity from mutation responses. */
public record ClubCommentModerationResult(Long id,ClubSourceRef source,CommentStatus status,boolean resourceHidden,boolean platformHidden,Long version) {
    public static ClubCommentModerationResult from(ClubComment c){return new ClubCommentModerationResult(c.getId(),c.source(),c.getStatus(),c.isResourceHidden(),c.isPlatformHidden(),c.getVersion());}
}
