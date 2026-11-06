package com.campusplus.backend.dto.clubinteraction;
import com.campusplus.backend.entity.*;
import java.time.Instant;
public record OwnClubCommentResponse(Long id,ClubSourceRef source,Long rootId,String body,CommentStatus status,boolean resourceHidden,
        boolean platformHidden,Instant createdAt,Instant updatedAt,Instant removedAt,Long version) {
    public static OwnClubCommentResponse from(ClubComment c){return new OwnClubCommentResponse(c.getId(),c.source(),
        c.getRoot()==null?null:c.getRoot().getId(),c.getBody(),c.getStatus(),c.isResourceHidden(),c.isPlatformHidden(),
        c.getCreatedAt(),c.getUpdatedAt(),c.getRemovedAt(),c.getVersion());}
    public static OwnClubCommentResponse from(ClubCommentRow c){return new OwnClubCommentResponse(c.id(),c.source(),c.rootId(),c.body(),c.status(),
        c.resourceHidden(),c.platformHidden(),c.createdAt(),c.updatedAt(),c.removedAt(),c.version());}
    @Override public String toString(){return "OwnClubCommentResponse[id="+id+",content=[REDACTED]]";}
}
