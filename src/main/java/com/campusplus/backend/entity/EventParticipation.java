package com.campusplus.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="event_participations",uniqueConstraints=@UniqueConstraint(columnNames={"event_id","user_id"}))
public class EventParticipation {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="event_id",nullable=false,updatable=false) private Event event;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id",nullable=false,updatable=false) private StudentProfile studentProfile;
    @Column(name="user_id",insertable=false,updatable=false) private Long userId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private EventParticipationStatus status=EventParticipationStatus.REGISTERED;
    @Column(name="registered_at",nullable=false) private Instant registeredAt;
    @Column(name="ended_at") private Instant endedAt;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="ended_by") private User endedBy;
    @Enumerated(EnumType.STRING) @Column(name="end_cause",length=32) private EventEndCause endCause;
    @Column(name="decision_reason",length=1000) private String decisionReason;
    @Column(name="checked_in_at") private Instant checkedInAt;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="checked_in_by") private User checkedInBy;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Version private Long version;

    protected EventParticipation() {}
    public EventParticipation(Event event,StudentProfile profile,Instant now) {
        this.event=event;studentProfile=profile;userId=profile.getUserId();registeredAt=now;createdAt=now;updatedAt=now;
    }
    public void registerAgain(Instant now) {
        status=EventParticipationStatus.REGISTERED;registeredAt=now;endedAt=null;endedBy=null;endCause=null;
        decisionReason=null;checkedInAt=null;checkedInBy=null;updatedAt=now;
    }
    public void end(EventParticipationStatus status,EventEndCause cause,User actor,String reason,Instant now) {
        this.status=status;endCause=cause;endedBy=actor;endedAt=now;decisionReason=reason;updatedAt=now;
    }
    public void checkIn(User actor,Instant now) { status=EventParticipationStatus.ATTENDED;checkedInAt=now;checkedInBy=actor;updatedAt=now; }
    public Long getId(){return id;} public Event getEvent(){return event;} public Long getUserId(){return userId;}
    public StudentProfile getStudentProfile(){return studentProfile;} public EventParticipationStatus getStatus(){return status;}
    public Instant getRegisteredAt(){return registeredAt;} public Instant getEndedAt(){return endedAt;}
    public EventEndCause getEndCause(){return endCause;} public String getDecisionReason(){return decisionReason;}
    public Instant getCheckedInAt(){return checkedInAt;} public Instant getCreatedAt(){return createdAt;}
    public Instant getUpdatedAt(){return updatedAt;} public Long getVersion(){return version;}
}
