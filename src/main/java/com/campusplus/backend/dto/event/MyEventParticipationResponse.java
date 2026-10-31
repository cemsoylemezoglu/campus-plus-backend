package com.campusplus.backend.dto.event;
import com.campusplus.backend.entity.*;
import java.time.Instant;
public record MyEventParticipationResponse(Long id,OwnEventSummary event,EventParticipationStatus status,
    Instant registeredAt,Instant endedAt,EventEndCause endCause,String decisionReason,Instant checkedInAt,Long version) {
    public static MyEventParticipationResponse from(EventParticipation p){return new MyEventParticipationResponse(p.getId(),
        OwnEventSummary.from(p.getEvent()),p.getStatus(),p.getRegisteredAt(),p.getEndedAt(),p.getEndCause(),
        p.getDecisionReason(),p.getCheckedInAt(),p.getVersion());}
}
