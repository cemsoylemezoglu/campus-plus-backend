package com.campusplus.backend.dto.comment;
import com.campusplus.backend.entity.*;
/** Local moderation may change its own flag but never receives platform-hidden text. */
public record CommentModerationResult(Long id,Long postId,CommentStatus status,boolean resourceHidden,boolean platformHidden,Long version) {
    public static CommentModerationResult from(PostComment c){return new CommentModerationResult(c.getId(),c.getPost().getId(),c.getStatus(),c.isResourceHidden(),c.isPlatformHidden(),c.getVersion());}
}
