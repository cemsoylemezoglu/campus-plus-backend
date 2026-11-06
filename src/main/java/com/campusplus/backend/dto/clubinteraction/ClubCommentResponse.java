package com.campusplus.backend.dto.clubinteraction;
import java.time.Instant;
public record ClubCommentResponse(Long id,ClubSourceRef source,Long rootId,Author author,String body,Instant createdAt,Instant updatedAt,
        Long version,boolean removed,long replyCount,long likeCount,boolean likedByMe) {
    public record Author(Long id,String name) {}
    @Override public String toString(){return "ClubCommentResponse[id="+id+",content=[REDACTED]]";}
}
