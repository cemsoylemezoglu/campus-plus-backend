package com.campusplus.backend.dto.notification;
import com.campusplus.backend.entity.*;
import java.time.Instant;
public record NotificationResponse(Long id,NotificationType type,Instant createdAt,Instant readAt,Long version,
        boolean available,String message,Target target) {
    public record Target(NotificationResource resource,Long id,Long clubId,Long eventId) {
        @Override public String toString(){return "NotificationTarget[REDACTED]";}
    }
    @Override public String toString(){return "NotificationResponse[id="+id+", content=[REDACTED]]";}
}
