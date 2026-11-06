package com.campusplus.backend.entity;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="club_comment_likes")
public class ClubCommentLike extends ContentLike {
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="comment_id",nullable=false,updatable=false) private ClubComment comment;
    protected ClubCommentLike() {}
    public ClubCommentLike(ClubComment comment,User user,Instant now){super(user,now);this.comment=comment;}
    public ClubComment getComment(){return comment;}
}
