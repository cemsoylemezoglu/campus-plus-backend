package com.campusplus.backend.service.announcement;

import com.campusplus.backend.dto.announcement.*;
import com.campusplus.backend.entity.*;
import com.campusplus.backend.service.notification.NotificationWriter;
import com.campusplus.backend.exception.ResourceConflictException;
import com.campusplus.backend.service.club.ClubWorkspace;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service @Validated @Transactional @PreAuthorize("isAuthenticated()")
public class AnnouncementService {
    private final AnnouncementWorkspace w;
    private final NotificationWriter notifications;
    public AnnouncementService(AnnouncementWorkspace w,NotificationWriter notifications){this.w=w;this.notifications=notifications;}
    public ManagedAnnouncementResponse create(@Positive Long clubId,@Valid CreateAnnouncementRequest request) {
        var ctx=w.managed(clubId,null,request.eventId());w.active(ctx.club());
        Event event=request.eventId()==null?null:ctx.events().get(request.eventId());
        w.checkLink(event,request.audience(),false);
        Announcement a=new Announcement(ctx.club(),ctx.actor(),request.title(),request.body(),request.audience(),event,w.core.clock.instant());
        return w.response(w.announcements.saveAndFlush(a),ctx.actor());
    }
    public ManagedAnnouncementResponse update(@Positive Long clubId,@Positive Long id,@Valid UpdateAnnouncementRequest request) {
        var ctx=w.managed(clubId,id,request.eventId());w.active(ctx.club());Announcement a=ctx.announcement();
        ClubWorkspace.version(a.getVersion(),request.version());w.policy.requireMutable(a);
        if(a.getStatus()!=AnnouncementStatus.DRAFT&&(request.audience()!=null||request.eventId()!=null||Boolean.TRUE.equals(request.clearEvent())))
            throw new ResourceConflictException("Yayından sonra kitle ve etkinlik bağlantısı değiştirilemez.");
        Event event=Boolean.TRUE.equals(request.clearEvent())?null:request.eventId()==null?a.getEvent():ctx.events().get(request.eventId());
        AnnouncementAudience audience=request.audience()==null?a.getAudience():request.audience();
        w.checkLink(event,audience,false);
        a.edit(request.title()==null?a.getTitle():request.title(),request.body()==null?a.getBody():request.body(),audience,event,w.core.clock.instant());
        return w.response(w.announcements.saveAndFlush(a),ctx.actor());
    }
    public ManagedAnnouncementResponse publish(@Positive Long clubId,@Positive Long id,@Valid AnnouncementActionRequest request) {
        var ctx=w.managed(clubId,id,null);w.active(ctx.club());Announcement a=ctx.announcement();
        ClubWorkspace.version(a.getVersion(),request.version());
        if(a.getArchivedAt()!=null||a.getStatus()!=AnnouncementStatus.DRAFT)throw new ResourceConflictException("Duyuru yayımlanamaz.");
        w.checkLink(a.getEvent(),a.getAudience(),true);
        a.publish(w.core.clock.instant());w.announcements.saveAndFlush(a);notifications.announcementPublished(a,ctx.actor());return w.response(a,ctx.actor());
    }
    public ManagedAnnouncementResponse withdraw(@Positive Long clubId,@Positive Long id,@Valid WithdrawAnnouncementRequest request) {
        var ctx=w.managed(clubId,id,null);w.lifecycleAllowed(ctx);Announcement a=ctx.announcement();
        ClubWorkspace.version(a.getVersion(),request.version());
        if(a.getArchivedAt()!=null||a.getStatus()!=AnnouncementStatus.PUBLISHED)throw new ResourceConflictException("Duyuru geri çekilemez.");
        a.withdraw(ctx.actor(),request.reason(),w.core.clock.instant());return w.response(w.announcements.saveAndFlush(a),ctx.actor());
    }
    public ManagedAnnouncementResponse archive(@Positive Long clubId,@Positive Long id,@Valid AnnouncementActionRequest request) {
        var ctx=w.managed(clubId,id,null);w.lifecycleAllowed(ctx);Announcement a=ctx.announcement();
        ClubWorkspace.version(a.getVersion(),request.version());
        if(a.getArchivedAt()!=null||a.getStatus()==AnnouncementStatus.PUBLISHED)
            throw new ResourceConflictException("Yayındaki duyuruyu önce geri çekiniz; arşiv işlemi yalnız bir kez yapılabilir.");
        a.archive(w.core.clock.instant());return w.response(w.announcements.saveAndFlush(a),ctx.actor());
    }
}
