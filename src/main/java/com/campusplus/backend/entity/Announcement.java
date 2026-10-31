package com.campusplus.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="club_announcements")
public class Announcement {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="club_id",nullable=false,updatable=false) private Club club;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="created_by",nullable=false,updatable=false) private User createdBy;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="event_id") private Event event;
    @Column(nullable=false,length=200) private String title;
    @Column(nullable=false,length=10000) private String body;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private AnnouncementAudience audience;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private AnnouncementStatus status=AnnouncementStatus.DRAFT;
    @Column(name="published_at") private Instant publishedAt;
    @Column(name="withdrawn_at") private Instant withdrawnAt;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="withdrawn_by") private User withdrawnBy;
    @Column(name="withdrawal_reason",length=1000) private String withdrawalReason;
    @Column(name="archived_at") private Instant archivedAt;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Version @Column(nullable=false) private Long version;

    protected Announcement() {}
    public Announcement(Club club,User creator,String title,String body,AnnouncementAudience audience,Event event,Instant now) {
        this.club=club;this.createdBy=creator;this.title=title;this.body=body;this.audience=audience;
        this.event=event;this.createdAt=now;this.updatedAt=now;
    }
    public void edit(String title,String body,AnnouncementAudience audience,Event event,Instant now) {
        this.title=title;this.body=body;this.audience=audience;this.event=event;this.updatedAt=now;
    }
    public void publish(Instant now) { status=AnnouncementStatus.PUBLISHED;publishedAt=now;updatedAt=now; }
    public void withdraw(User actor,String reason,Instant now) {
        status=AnnouncementStatus.WITHDRAWN;withdrawnBy=actor;withdrawalReason=reason;withdrawnAt=now;updatedAt=now;
    }
    public void archive(Instant now) { archivedAt=now;updatedAt=now; }
    public Long getId(){return id;}
    public Club getClub(){return club;}
    public Event getEvent(){return event;}
    public String getTitle(){return title;}
    public String getBody(){return body;}
    public AnnouncementAudience getAudience(){return audience;}
    public AnnouncementStatus getStatus(){return status;}
    public Instant getPublishedAt(){return publishedAt;}
    public Instant getWithdrawnAt(){return withdrawnAt;}
    public String getWithdrawalReason(){return withdrawalReason;}
    public Instant getArchivedAt(){return archivedAt;}
    public Instant getCreatedAt(){return createdAt;}
    public Instant getUpdatedAt(){return updatedAt;}
    public Long getVersion(){return version;}
}
