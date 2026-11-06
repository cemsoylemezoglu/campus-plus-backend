package com.campusplus.backend.dto.clubinteraction;
import com.campusplus.backend.entity.*;
import java.time.Instant;
public record ClubCommentRow(Long id,Long eventId,Long announcementId,Long rootId,Long authorId,String authorName,String body,CommentStatus status,
        boolean resourceHidden,boolean platformHidden,Instant createdAt,Instant updatedAt,Instant removedAt,Long version) {
    public ClubSourceRef source(){return new ClubSourceRef(eventId!=null?ClubSourceKind.EVENT:ClubSourceKind.ANNOUNCEMENT,eventId!=null?eventId:announcementId);}
    public ClubCommentResponse response(long replies,long likes,boolean liked) {
        boolean removed=status==CommentStatus.REMOVED;
        return new ClubCommentResponse(id,source(),rootId,removed?null:new ClubCommentResponse.Author(authorId,authorName),removed?null:body,
                createdAt,updatedAt,version,removed,replies,removed?0:likes,!removed&&liked);
    }
    @Override public String toString(){return "ClubCommentRow[id="+id+",content=[REDACTED]]";}
}
