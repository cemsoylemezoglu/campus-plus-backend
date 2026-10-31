package com.campusplus.backend.dto.event;
import java.time.Instant;
public record EventResponse(EventSummary event,String description,String coverUrl,Instant registrationOpensAt,
    Instant registrationClosesAt,Instant publishedAt,Instant cancelledAt,String cancellationReason,Instant completedAt,
    Instant scheduleChangedAt,String scheduleChangeReason,Instant createdAt,Instant updatedAt) {}
