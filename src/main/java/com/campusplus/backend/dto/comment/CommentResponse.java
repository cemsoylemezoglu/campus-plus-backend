package com.campusplus.backend.dto.comment;
import java.time.Instant;
public record CommentResponse(Long id,Long postId,Long rootId,Author author,String body,Instant createdAt,Instant updatedAt,
        Long version,boolean removed,long replyCount,long likeCount,boolean likedByMe) {
    public record Author(Long id,String name) {}
    @Override public String toString(){return "CommentResponse[id="+id+",content=[REDACTED]]";}
}
