package com.campusplus.backend.dto.event;
import com.campusplus.backend.entity.*;
import java.time.Instant;
public record ManagedEventParticipationResponse(Long id,Long userId,String displayName,EventParticipationStatus status,
    Instant registeredAt,Instant endedAt,EventEndCause endCause,String decisionReason,Instant checkedInAt,Long version) {
    public static ManagedEventParticipationResponse from(EventParticipation p,User user){return new ManagedEventParticipationResponse(
        p.getId(),p.getUserId(),user.getName(),p.getStatus(),p.getRegisteredAt(),
        p.getEndedAt(),p.getEndCause(),p.getDecisionReason(),p.getCheckedInAt(),p.getVersion());}
}
