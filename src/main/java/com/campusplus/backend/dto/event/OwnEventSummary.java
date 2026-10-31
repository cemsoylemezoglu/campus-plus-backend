package com.campusplus.backend.dto.event;
import com.campusplus.backend.entity.*;
import java.time.Instant;
public record OwnEventSummary(Long id,Long clubId,String clubName,String title,Instant startsAt,Instant endsAt,
    String timeZone,EventStatus status,Instant archivedAt,Long version) {
    public static OwnEventSummary from(Event e){return new OwnEventSummary(e.getId(),e.getClub().getId(),e.getClub().getName(),
        e.getTitle(),e.getStartsAt(),e.getEndsAt(),e.getTimeZone(),e.getStatus(),e.getArchivedAt(),e.getVersion());}
}
