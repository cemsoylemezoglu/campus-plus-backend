package com.campusplus.backend.entity;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="post_likes")
public class PostLike extends ContentLike {
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="post_id",nullable=false,updatable=false) private Post post;
    protected PostLike() {}
    public PostLike(Post post,User user,Instant now){super(user,now);this.post=post;}
    public Post getPost(){return post;}
}
