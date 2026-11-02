package com.campusplus.backend.entity;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="comment_moderation_actions")
public class CommentModeration {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="comment_id",nullable=false,updatable=false) private PostComment comment;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="actor_id",nullable=false,updatable=false) private User actor;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16,updatable=false) private CommentModerationScope scope;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16,updatable=false) private CommentModerationAction action;
    @Column(nullable=false,length=1000,updatable=false) private String reason;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="resulting_version",nullable=false,updatable=false) private Long resultingVersion;
    protected CommentModeration() {}
    public CommentModeration(PostComment c,User actor,CommentModerationScope scope,boolean hidden,String reason,Instant now) {
        comment=c;this.actor=actor;this.scope=scope;action=hidden?CommentModerationAction.HIDE:CommentModerationAction.RESTORE;
        this.reason=reason;createdAt=now;resultingVersion=c.getVersion();
    }
}
