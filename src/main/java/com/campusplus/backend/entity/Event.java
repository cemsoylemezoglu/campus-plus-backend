package com.campusplus.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="events")
public class Event {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="club_id",nullable=false,updatable=false) private Club club;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="created_by",nullable=false,updatable=false) private User createdBy;
    @Column(nullable=false,length=200) private String title;
    @Column(nullable=false,length=10000) private String description;
    @Column(nullable=false,length=500) private String venue;
    @Column(name="cover_url",length=500) private String coverUrl;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private EventStatus status=EventStatus.DRAFT;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private EventAudience audience;
    private Integer capacity;
    @Column(name="registration_opens_at",nullable=false) private Instant registrationOpensAt;
    @Column(name="registration_closes_at",nullable=false) private Instant registrationClosesAt;
    @Column(name="starts_at",nullable=false) private Instant startsAt;
    @Column(name="ends_at",nullable=false) private Instant endsAt;
    @Column(name="time_zone",nullable=false,length=64) private String timeZone;
    @Column(name="published_at") private Instant publishedAt;
    @Column(name="cancelled_at") private Instant cancelledAt;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="cancelled_by") private User cancelledBy;
    @Column(name="cancellation_reason",length=1000) private String cancellationReason;
    @Column(name="completed_at") private Instant completedAt;
    @Column(name="archived_at") private Instant archivedAt;
    @Column(name="schedule_changed_at") private Instant scheduleChangedAt;
    @Column(name="schedule_change_reason",length=1000) private String scheduleChangeReason;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Version private Long version;

    protected Event() {}
    public Event(Club club,User creator,String title,String description,String venue,String coverUrl,
                 EventAudience audience,Integer capacity,Instant opens,Instant closes,Instant starts,Instant ends,String zone,Instant now) {
        this.club=club;createdBy=creator;this.title=title;this.description=description;this.venue=venue;
        this.coverUrl=coverUrl;this.audience=audience;this.capacity=capacity;
        registrationOpensAt=opens;registrationClosesAt=closes;startsAt=starts;endsAt=ends;timeZone=zone;
        createdAt=now;updatedAt=now;
    }
    public void edit(String title,String description,String venue,String coverUrl,EventAudience audience,Integer capacity,Instant now) {
        this.title=title;this.description=description;this.venue=venue;this.coverUrl=coverUrl;this.audience=audience;this.capacity=capacity;updatedAt=now;
    }
    public void reschedule(Instant opens,Instant closes,Instant starts,Instant ends,String zone,String reason,Instant now) {
        registrationOpensAt=opens;registrationClosesAt=closes;startsAt=starts;endsAt=ends;timeZone=zone;
        scheduleChangedAt=now;scheduleChangeReason=reason;updatedAt=now;
    }
    public void publish(Instant now) { status=EventStatus.PUBLISHED;publishedAt=now;updatedAt=now; }
    public void cancel(User actor,String reason,Instant now) {
        status=EventStatus.CANCELLED;cancelledBy=actor;cancelledAt=now;cancellationReason=reason;updatedAt=now;
    }
    public void complete(Instant now) { status=EventStatus.COMPLETED;completedAt=now;updatedAt=now; }
    public void archive(Instant now) { archivedAt=now;updatedAt=now; }
    public Long getId(){return id;} public Club getClub(){return club;}
    public String getTitle(){return title;} public String getDescription(){return description;}
    public String getVenue(){return venue;} public String getCoverUrl(){return coverUrl;}
    public EventStatus getStatus(){return status;} public EventAudience getAudience(){return audience;}
    public Integer getCapacity(){return capacity;}
    public Instant getRegistrationOpensAt(){return registrationOpensAt;} public Instant getRegistrationClosesAt(){return registrationClosesAt;}
    public Instant getStartsAt(){return startsAt;} public Instant getEndsAt(){return endsAt;} public String getTimeZone(){return timeZone;}
    public Instant getPublishedAt(){return publishedAt;} public Instant getArchivedAt(){return archivedAt;}
    public Instant getCancelledAt(){return cancelledAt;} public String getCancellationReason(){return cancellationReason;}
    public Instant getCompletedAt(){return completedAt;} public Instant getScheduleChangedAt(){return scheduleChangedAt;}
    public String getScheduleChangeReason(){return scheduleChangeReason;}
    public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;} public Long getVersion(){return version;}
}
