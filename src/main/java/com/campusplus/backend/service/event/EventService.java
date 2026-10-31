package com.campusplus.backend.service.event;

import com.campusplus.backend.dto.event.*;
import com.campusplus.backend.entity.*;
import com.campusplus.backend.service.notification.NotificationWriter;
import com.campusplus.backend.exception.ResourceConflictException;
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
public class EventService {
    private final EventWorkspace w;
    private final NotificationWriter notifications;
    public EventService(EventWorkspace w,NotificationWriter notifications){this.w=w;this.notifications=notifications;}
    public EventResponse create(@Positive Long clubId,@Valid CreateEventRequest request) {
        var ctx=w.managed(clubId,null,List.of());w.active(ctx.club());Instant now=w.core.clock.instant();
        future(request.startsAt().toInstant(),request.registrationClosesAt().toInstant(),now);
        Event e=new Event(ctx.club(),ctx.actor(),request.title(),request.description(),request.venue(),request.coverUrl(),
                request.audience(),request.capacity(),request.registrationOpensAt().toInstant(),request.registrationClosesAt().toInstant(),
                request.startsAt().toInstant(),request.endsAt().toInstant(),request.timeZone(),now);
        return w.response(w.events.saveAndFlush(e));
    }
    public EventResponse update(@Positive Long clubId,@Positive Long id,@Valid UpdateEventRequest request) {
        var ctx=w.managed(clubId,id,List.of());w.active(ctx.club());var e=ctx.event();
        ClubWorkspace.version(e.getVersion(),request.version());Instant now=w.core.clock.instant();w.policy.requireMutable(e,now);
        if(request.audience()!=null && e.getStatus()!=EventStatus.DRAFT)
            throw new ResourceConflictException("Yayımlanmış etkinliğin katılım politikası değiştirilemez.");
        Integer capacity=Boolean.TRUE.equals(request.unlimited())?null:request.capacity()==null?e.getCapacity():request.capacity();
        if(capacity!=null && capacity<w.occupied(e)) throw new ResourceConflictException("Kapasite mevcut katılım sayısından küçük olamaz.");
        e.edit(request.title()==null?e.getTitle():request.title(),request.description()==null?e.getDescription():request.description(),
                request.venue()==null?e.getVenue():request.venue(),request.coverUrl()==null?e.getCoverUrl():EventInput.optional(request.coverUrl()),
                request.audience()==null?e.getAudience():request.audience(),capacity,now);
        return w.response(w.events.saveAndFlush(e));
    }
    public EventResponse publish(@Positive Long clubId,@Positive Long id,@Valid EventActionRequest request) {
        var ctx=w.managed(clubId,id,List.of());w.active(ctx.club());Event e=ctx.event();
        ClubWorkspace.version(e.getVersion(),request.version());w.policy.requireStatus(e,EventStatus.DRAFT);
        Instant now=w.core.clock.instant();future(e.getStartsAt(),e.getRegistrationClosesAt(),now);
        e.publish(now);w.events.saveAndFlush(e);notifications.eventPublished(e,ctx.actor());return w.response(e);
    }
    public EventResponse reschedule(@Positive Long clubId,@Positive Long id,@Valid RescheduleEventRequest request) {
        var ctx=w.managed(clubId,id,List.of());w.active(ctx.club());Event e=ctx.event();
        ClubWorkspace.version(e.getVersion(),request.version());Instant now=w.core.clock.instant();w.policy.requireMutable(e,now);
        if(w.participants.existsByEventIdAndStatus(id,EventParticipationStatus.ATTENDED)) throw new ResourceConflictException("Giriş kontrolü başlamış etkinlik ertelenemez.");
        String reason=e.getStatus()==EventStatus.PUBLISHED?ClubWorkspace.reason(request.reason()):request.reason();
        future(request.startsAt().toInstant(),request.registrationClosesAt().toInstant(),now);
        e.reschedule(request.registrationOpensAt().toInstant(),request.registrationClosesAt().toInstant(),request.startsAt().toInstant(),
                request.endsAt().toInstant(),request.timeZone(),reason,now);
        w.events.saveAndFlush(e);
        if(e.getStatus()==EventStatus.PUBLISHED)notifications.eventChanged(e,ctx.actor(),NotificationType.EVENT_RESCHEDULED);
        return w.response(e);
    }
    public EventResponse cancel(@Positive Long clubId,@Positive Long id,@Valid EventReasonRequest request) {
        var ctx=w.managed(clubId,id,List.of());Event e=ctx.event();
        w.policy.requireAdministrativeLifecycle(ctx.club(),ctx.actor());ClubWorkspace.version(e.getVersion(),request.version());
        if(e.getArchivedAt()!=null || (e.getStatus()!=EventStatus.DRAFT && e.getStatus()!=EventStatus.PUBLISHED)) throw new ResourceConflictException("Etkinlik iptal edilemez.");
        Instant now=w.core.clock.instant();e.cancel(ctx.actor(),request.reason(),now);w.events.saveAndFlush(e);
        if(e.getPublishedAt()!=null)notifications.eventChanged(e,ctx.actor(),NotificationType.EVENT_CANCELLED);
        w.participants.endRegistered(id,null,EventParticipationStatus.CANCELLED,EventEndCause.EVENT_CANCELLED,ctx.actor(),null,now);
        return w.response(e);
    }
    public EventResponse complete(@Positive Long clubId,@Positive Long id,@Valid EventActionRequest request) {
        var ctx=w.managed(clubId,id,List.of());Event e=ctx.event();
        w.policy.requireAdministrativeLifecycle(ctx.club(),ctx.actor());ClubWorkspace.version(e.getVersion(),request.version());
        w.policy.requireStatus(e,EventStatus.PUBLISHED);Instant now=w.core.clock.instant();
        if(now.isBefore(e.getEndsAt())) throw new ResourceConflictException("Etkinlik henüz bitmedi.");
        e.complete(now);w.events.saveAndFlush(e);
        w.participants.endRegistered(id,null,EventParticipationStatus.NO_SHOW,EventEndCause.NO_SHOW,ctx.actor(),null,now);
        return w.response(e);
    }
    public EventResponse archive(@Positive Long clubId,@Positive Long id,@Valid EventActionRequest request) {
        var ctx=w.managed(clubId,id,List.of());Event e=ctx.event();
        w.policy.requireAdministrativeLifecycle(ctx.club(),ctx.actor());ClubWorkspace.version(e.getVersion(),request.version());
        if(e.getArchivedAt()!=null || (e.getStatus()!=EventStatus.CANCELLED && e.getStatus()!=EventStatus.COMPLETED)) throw new ResourceConflictException("Önce etkinliği sonlandırınız.");
        e.archive(w.core.clock.instant());return w.response(w.events.saveAndFlush(e));
    }
    private void future(Instant starts,Instant closes,Instant now) {
        if(!now.isBefore(starts)||!now.isBefore(closes)) throw new ResourceConflictException("Başlangıç ve kayıt kapanışı gelecekte olmalı.");
    }
}
