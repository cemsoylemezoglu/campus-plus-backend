package com.campusplus.backend.dto.announcement;
import com.campusplus.backend.entity.*;
import java.time.Instant;
public record AnnouncementSummary(Long id,AnnouncementClubSummary club,String title,AnnouncementAudience audience,
        Instant publishedAt,Instant updatedAt,Long version) {
    public static AnnouncementSummary from(Announcement a) {
        return new AnnouncementSummary(a.getId(),AnnouncementClubSummary.from(a.getClub()),a.getTitle(),a.getAudience(),
                a.getPublishedAt(),a.getUpdatedAt(),a.getVersion());
    }
    @Override public String toString(){return "AnnouncementSummary[id="+id+", content=[REDACTED]]";}
}
