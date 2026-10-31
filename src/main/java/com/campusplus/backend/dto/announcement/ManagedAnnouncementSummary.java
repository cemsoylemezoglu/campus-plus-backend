package com.campusplus.backend.dto.announcement;
import com.campusplus.backend.entity.*;
import java.time.Instant;
public record ManagedAnnouncementSummary(AnnouncementSummary announcement,AnnouncementStatus status,Instant archivedAt) {
    public static ManagedAnnouncementSummary from(Announcement a) {return new ManagedAnnouncementSummary(AnnouncementSummary.from(a),a.getStatus(),a.getArchivedAt());}
}
