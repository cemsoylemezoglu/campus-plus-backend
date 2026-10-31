package com.campusplus.backend.dto.announcement;
import com.campusplus.backend.entity.*;
import java.time.Instant;
public record ManagedAnnouncementResponse(AnnouncementSummary announcement,AnnouncementStatus status,Instant archivedAt,String body,LinkedAnnouncementEvent event,
        Instant createdAt,Instant withdrawnAt,String withdrawalReason) {
    public static ManagedAnnouncementResponse from(Announcement a,LinkedAnnouncementEvent event) {
        return new ManagedAnnouncementResponse(AnnouncementSummary.from(a),a.getStatus(),a.getArchivedAt(),a.getBody(),event,
                a.getCreatedAt(),a.getWithdrawnAt(),a.getWithdrawalReason());
    }
    @Override public String toString(){return "ManagedAnnouncementResponse[content=[REDACTED]]";}
}
