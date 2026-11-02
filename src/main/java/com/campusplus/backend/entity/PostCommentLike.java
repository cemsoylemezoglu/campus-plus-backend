package com.campusplus.backend.entity;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="post_comment_likes")
public class PostCommentLike extends ContentLike {
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="comment_id",nullable=false,updatable=false) private PostComment comment;
    protected PostCommentLike() {}
    public PostCommentLike(PostComment comment,User user,Instant now){super(user,now);this.comment=comment;}
    public PostComment getComment(){return comment;}
}
