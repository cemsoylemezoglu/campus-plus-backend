package com.campusplus.backend.service.event;

import com.campusplus.backend.dto.event.*;
import com.campusplus.backend.entity.*;
import com.campusplus.backend.service.notification.NotificationWriter;
import com.campusplus.backend.exception.*;
import com.campusplus.backend.service.club.ClubWorkspace;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service @Validated @Transactional @PreAuthorize("isAuthenticated()")
public class EventParticipationService {
    private final EventWorkspace w;
    private final EventSettings settings;
    private final NotificationWriter notifications;
    public EventParticipationService(EventWorkspace w,EventSettings settings,NotificationWriter notifications){this.w=w;this.settings=settings;this.notifications=notifications;}
    public MyEventParticipationResponse register(@Positive Long eventId,@Valid RegisterEventRequest request) {
        var ctx=w.visible(eventId);Event e=ctx.event();w.active(ctx.club());
        ClubWorkspace.version(e.getVersion(),request.eventVersion());
        StudentProfile profile=w.eligible(e,ctx.actor());Instant now=w.core.clock.instant();w.policy.requireOpen(e,now);
        EventParticipation p=w.participants.findByEventIdAndUserId(eventId,ctx.actor().getId()).orElse(null);
        if(p!=null) {
            p=w.own(e,ctx.actor().getId());ClubWorkspace.version(p.getVersion(),request.participationVersion());
            require(p,EventParticipationStatus.CANCELLED);
        } else if(request.participationVersion()!=null) throw new ResourceConflictException("İlk kayıt için katılım sürümü gönderilmemeli.");
        if(e.getCapacity()!=null && w.occupied(e)>=e.getCapacity()) throw new ResourceConflictException("Etkinlik kontenjanı dolu.");
        if(p==null) p=new EventParticipation(e,profile,now);else p.registerAgain(now);
        w.participants.saveAndFlush(p);notifications.participationChanged(p,ctx.actor(),NotificationType.PARTICIPATION_REGISTERED);
        return MyEventParticipationResponse.from(p);
    }
    public MyEventParticipationResponse cancel(@Positive Long eventId,@Valid EventActionRequest request) {
        var ctx=w.owned(eventId);Event e=ctx.event();var p=w.own(e,ctx.actor().getId());
        ClubWorkspace.version(p.getVersion(),request.version());require(p,EventParticipationStatus.REGISTERED);
        Instant now=w.core.clock.instant();w.policy.requireStatus(e,EventStatus.PUBLISHED);
        if(!now.isBefore(e.getStartsAt())) throw new ResourceConflictException("Kişisel iptal süresi doldu.");
        p.end(EventParticipationStatus.CANCELLED,EventEndCause.SELF_CANCELLED,ctx.actor(),null,now);
        return MyEventParticipationResponse.from(w.participants.saveAndFlush(p));
    }
    public enum Decision { REMOVE, ALLOW_REREGISTRATION, CHECK_IN }
    public ManagedEventParticipationResponse decide(@Positive Long clubId,@Positive Long eventId,@Positive Long participationId,
            @Valid EventActionRequest request,@jakarta.validation.constraints.Size(max=1000) String reason,
            @jakarta.validation.constraints.NotNull Decision decision) {
        w.preflightManager(clubId);
        w.eventOfClub(eventId,clubId);
        var previewPart=w.participants.findByIdAndEventId(participationId,eventId).orElseThrow(() -> new ResourceNotFoundException("Katılım bulunamadı."));
        var ctx=w.managed(clubId,eventId,List.of(previewPart.getUserId()));Event e=ctx.event();w.active(ctx.club());
        var p=w.participation(eventId,participationId);ClubWorkspace.version(p.getVersion(),request.version());
        w.policy.requireStatus(e,EventStatus.PUBLISHED);
        if(decision==Decision.CHECK_IN) {
            require(p,EventParticipationStatus.REGISTERED);w.eligible(e,ctx.users().get(p.getUserId()));
            Instant now=w.core.clock.instant();
            if(now.isBefore(e.getStartsAt().minus(settings.checkInLead()))||now.isAfter(e.getEndsAt())) throw new ResourceConflictException("Giriş kontrolü zaman aralığı açık değil.");
            p.checkIn(ctx.actor(),now);
        } else if(decision==Decision.REMOVE) {
            require(p,EventParticipationStatus.REGISTERED);
            String note=ClubWorkspace.reason(reason);
            if(note.length()>1000) throw new InvalidRequestException("Gerekçe en fazla 1000 karakter olmalı.");
            p.end(EventParticipationStatus.REMOVED,EventEndCause.REMOVED,ctx.actor(),note,w.core.clock.instant());
        } else {
            require(p,EventParticipationStatus.REMOVED);
            p.end(EventParticipationStatus.CANCELLED,EventEndCause.REAPPLICATION_ALLOWED,ctx.actor(),null,w.core.clock.instant());
        }
        w.participants.saveAndFlush(p);
        NotificationType type=switch(decision){case REMOVE -> NotificationType.PARTICIPATION_REMOVED;case ALLOW_REREGISTRATION -> NotificationType.PARTICIPATION_REAPPLICATION_ALLOWED;case CHECK_IN -> NotificationType.PARTICIPATION_CHECKED_IN;};
        notifications.participationChanged(p,ctx.actor(),type);
        return ManagedEventParticipationResponse.from(p,ctx.users().get(p.getUserId()));
    }
    private void require(EventParticipation p,EventParticipationStatus status) {
        if(p.getStatus()!=status) throw new ResourceConflictException("Katılım bu işleme uygun değil.");
    }
}
