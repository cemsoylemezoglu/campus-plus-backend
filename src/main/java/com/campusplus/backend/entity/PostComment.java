package com.campusplus.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="post_comments")
public class PostComment {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="post_id",nullable=false,updatable=false) private Post post;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="author_id",nullable=false,updatable=false) private User author;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="root_id",updatable=false) private PostComment root;
    @Column(nullable=false,length=3000) private String body;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private CommentStatus status=CommentStatus.ACTIVE;
    @Column(name="resource_hidden",nullable=false) private boolean resourceHidden;
    @Column(name="platform_hidden",nullable=false) private boolean platformHidden;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Column(name="removed_at") private Instant removedAt;
    @Version @Column(nullable=false) private Long version;
    protected PostComment() {}
    public PostComment(Post post,User author,PostComment root,String body,Instant now) {
        this.post=post;this.author=author;this.root=root;this.body=body;createdAt=now;updatedAt=now;
    }
    private Instant touch(Instant now){updatedAt=now.isBefore(updatedAt)?updatedAt:now;return updatedAt;}
    public void edit(String body,Instant now){this.body=body;touch(now);}
    public void remove(Instant now){status=CommentStatus.REMOVED;removedAt=touch(now);}
    public void moderate(CommentModerationScope scope,boolean hidden,Instant now) {
        if(scope==CommentModerationScope.PLATFORM)platformHidden=hidden;else resourceHidden=hidden;touch(now);
    }
    public boolean hidden(){return resourceHidden||platformHidden;}
    public boolean visibleContent(){return status==CommentStatus.ACTIVE&&!hidden()&&(root==null||!root.hidden());}
    public Long getId(){return id;}
    public Post getPost(){return post;}
    public User getAuthor(){return author;}
    public PostComment getRoot(){return root;}
    public String getBody(){return body;}
    public CommentStatus getStatus(){return status;}
    public boolean isResourceHidden(){return resourceHidden;}
    public boolean isPlatformHidden(){return platformHidden;}
    public Instant getCreatedAt(){return createdAt;}
    public Instant getUpdatedAt(){return updatedAt;}
    public Instant getRemovedAt(){return removedAt;}
    public Long getVersion(){return version;}
}
