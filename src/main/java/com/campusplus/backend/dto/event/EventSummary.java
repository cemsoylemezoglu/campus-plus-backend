package com.campusplus.backend.dto.event;
import com.campusplus.backend.entity.*;
import java.time.Instant;
public record EventSummary(Long id,EventClubSummary club,String title,String venue,Instant startsAt,Instant endsAt,
    String timeZone,EventStatus status,EventAudience audience,Integer capacity,long occupiedSeatCount,Long availableSeats,
    boolean registrationOpen,Instant archivedAt,Long version) {}
