package com.campusplus.backend.dto.announcement;
import com.campusplus.backend.entity.*;
import java.time.Instant;
public record LinkedAnnouncementEvent(Long id,String title,EventStatus status,Instant startsAt) {
    public static LinkedAnnouncementEvent from(Event e) {return new LinkedAnnouncementEvent(e.getId(),e.getTitle(),e.getStatus(),e.getStartsAt());}
    @Override public String toString(){return "LinkedAnnouncementEvent[id="+id+", content=[REDACTED]]";}
}
