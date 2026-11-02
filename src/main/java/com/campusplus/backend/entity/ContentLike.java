package com.campusplus.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@MappedSuperclass
public abstract class ContentLike {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id",nullable=false,updatable=false) private User user;
    @Column(nullable=false) private boolean active=true;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Version @Column(nullable=false) private Long version;
    protected ContentLike() {}
    protected ContentLike(User user,Instant now){this.user=user;createdAt=now;updatedAt=now;}
    public void setActive(boolean value,Instant now){active=value;updatedAt=now.isBefore(updatedAt)?updatedAt:now;}
    public Long getId(){return id;}
    public User getUser(){return user;}
    public boolean isActive(){return active;}
    public Long getVersion(){return version;}
}
