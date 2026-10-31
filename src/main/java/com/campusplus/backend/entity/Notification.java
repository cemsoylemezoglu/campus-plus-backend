package com.campusplus.backend.entity;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="notifications",uniqueConstraints=@UniqueConstraint(columnNames={"recipient_id","source_key"}))
public class Notification {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="recipient_id",nullable=false,updatable=false) private User recipient;
    @Column(name="recipient_id",insertable=false,updatable=false) private Long recipientId;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="actor_id",updatable=false) private User actor;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=64,updatable=false) private NotificationType type;
    @Column(name="source_key",nullable=false,length=200,updatable=false) private String sourceKey;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="club_id",updatable=false) private Club club;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="announcement_id",updatable=false) private Announcement announcement;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="event_id",updatable=false) private Event event;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="participation_id",updatable=false) private EventParticipation participation;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="membership_id",updatable=false) private ClubMembership membership;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="read_at") private Instant readAt;
    @Version @Column(nullable=false) private Long version;
    protected Notification() {}
    public void read(Instant now){readAt=now.isBefore(createdAt)?createdAt:now;}
    public void unread(){readAt=null;}
    public Long getId(){return id;}
    public Long getRecipientId(){return recipientId;}
    public NotificationType getType(){return type;}
    public Club getClub(){return club;}
    public Announcement getAnnouncement(){return announcement;}
    public Event getEvent(){return event;}
    public EventParticipation getParticipation(){return participation;}
    public ClubMembership getMembership(){return membership;}
    public Instant getCreatedAt(){return createdAt;}
    public Instant getReadAt(){return readAt;}
    public Long getVersion(){return version;}
}
