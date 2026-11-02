package com.campusplus.backend.dto.comment;
import com.campusplus.backend.entity.*;
import java.time.Instant;
public record CommentRow(Long id,Long postId,Long rootId,Long authorId,String authorName,String body,CommentStatus status,
        boolean resourceHidden,boolean platformHidden,Instant createdAt,Instant updatedAt,Instant removedAt,Long version) {
    public CommentResponse response(long replies,long likes,boolean liked) {
        boolean removed=status==CommentStatus.REMOVED;
        return new CommentResponse(id,postId,rootId,removed?null:new CommentResponse.Author(authorId,authorName),removed?null:body,
                createdAt,updatedAt,version,removed,replies,removed?0:likes,!removed&&liked);
    }
    @Override public String toString(){return "CommentRow[id="+id+",content=[REDACTED]]";}
}
