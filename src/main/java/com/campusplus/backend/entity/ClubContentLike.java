package com.campusplus.backend.entity;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="club_content_likes")
public class ClubContentLike extends ContentLike {
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="event_id",updatable=false) private Event event;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="announcement_id",updatable=false) private Announcement announcement;
    protected ClubContentLike() {}
    public ClubContentLike(Event event,Announcement announcement,User user,Instant now){super(user,now);
        if((event==null)==(announcement==null))throw new IllegalArgumentException("Exactly one source is required");
        this.event=event;this.announcement=announcement;}
    public Event getEvent(){return event;}
    public Announcement getAnnouncement(){return announcement;}
}
