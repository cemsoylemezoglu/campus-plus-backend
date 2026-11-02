package com.campusplus.backend.dto.comment;
import com.campusplus.backend.entity.*;
import java.time.Instant;
public record OwnCommentResponse(Long id,Long postId,Long rootId,String body,CommentStatus status,boolean resourceHidden,
        boolean platformHidden,Instant createdAt,Instant updatedAt,Instant removedAt,Long version) {
    public static OwnCommentResponse from(PostComment c){return new OwnCommentResponse(c.getId(),c.getPost().getId(),
        c.getRoot()==null?null:c.getRoot().getId(),c.getBody(),c.getStatus(),c.isResourceHidden(),c.isPlatformHidden(),
        c.getCreatedAt(),c.getUpdatedAt(),c.getRemovedAt(),c.getVersion());}
    public static OwnCommentResponse from(CommentRow c){return new OwnCommentResponse(c.id(),c.postId(),c.rootId(),c.body(),c.status(),
        c.resourceHidden(),c.platformHidden(),c.createdAt(),c.updatedAt(),c.removedAt(),c.version());}
    @Override public String toString(){return "OwnCommentResponse[id="+id+",content=[REDACTED]]";}
}
