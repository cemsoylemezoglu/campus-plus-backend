package com.campusplus.backend.service.announcement;

import com.campusplus.backend.dto.announcement.*;
import com.campusplus.backend.entity.*;
import com.campusplus.backend.exception.*;
import com.campusplus.backend.repository.*;
import com.campusplus.backend.security.*;
import com.campusplus.backend.service.club.ClubWorkspace;
import jakarta.persistence.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** Transactional writers: actor -> club SHARE -> linked events ASC -> announcement WRITE. */
@Component
public class AnnouncementWorkspace {
    public final ClubWorkspace core;
    public final AnnouncementRepository announcements;
    public final AnnouncementPolicy policy;
    private final EventRepository events;
    private final EventPolicy eventPolicy;
    private final EntityManager em;
    public AnnouncementWorkspace(ClubWorkspace core,AnnouncementRepository announcements,AnnouncementPolicy policy,
            EventRepository events,EventPolicy eventPolicy,EntityManager em) {
        this.core=core;this.announcements=announcements;this.policy=policy;
        this.events=events;this.eventPolicy=eventPolicy;this.em=em;
    }
    public record Locked(Club club,User actor,Announcement announcement,Map<Long,Event> events) {}
    public Announcement announcement(Long id) {
        return announcements.findById(id).orElseThrow(() -> new ResourceNotFoundException("Duyuru bulunamadı."));
    }
    public void preflightManager(Long clubId) {
        Club c=core.club(clubId);User actor=core.actor();policy.requireManager(c,actor,core.local(c,actor.getId()));
    }
    public Locked managed(Long clubId,Long id,Long requestedEvent) {
        preflightManager(clubId);
        Long previewEvent=null;
        if(id!=null) {
            Announcement preview=announcements.findByIdAndClubId(id,clubId)
                .orElseThrow(() -> new ResourceNotFoundException("Duyuru bulunamadı."));
            previewEvent=preview.getEvent()==null?null:preview.getEvent().getId();
        }
        User actor=core.lockUser(core.current.userId());
        Club club=core.clubs.findByIdForShare(clubId).orElseThrow(() -> new ResourceNotFoundException("Kulüp bulunamadı."));
        em.refresh(club,LockModeType.PESSIMISTIC_READ);
        policy.requireManager(club,actor,core.local(club,actor.getId()));
        SortedSet<Long> ids=new TreeSet<>();if(previewEvent!=null)ids.add(previewEvent);if(requestedEvent!=null)ids.add(requestedEvent);
        Map<Long,Event> lockedEvents=new LinkedHashMap<>();
        for(Long eventId:ids) {
            Event event=events.findForUpdate(eventId,clubId)
                .orElseThrow(() -> new ResourceConflictException("Etkinlik bu kulübe ait değil veya bulunamadı."));
            em.refresh(event,LockModeType.PESSIMISTIC_WRITE);lockedEvents.put(eventId,event);
        }
        Announcement a=null;
        if(id!=null) {
            a=announcements.findForUpdate(id,clubId).orElseThrow(() -> new ResourceNotFoundException("Duyuru bulunamadı."));
            em.refresh(a,LockModeType.PESSIMISTIC_WRITE);
            Long actualEvent=a.getEvent()==null?null:a.getEvent().getId();
            if(!Objects.equals(previewEvent,actualEvent))
                throw new ResourceConflictException("Duyuru bağlantısı değişti; güncel sürümü alınız.");
        }
        return new Locked(club,actor,a,lockedEvents);
    }
    public void active(Club club) {
        core.policy.requireActive(club);
        University university=core.universities.findByIdForShare(club.getUniversity().getId())
            .orElseThrow(() -> new ResourceNotFoundException("Üniversite bulunamadı."));
        core.refreshUniversity(university);
        if(!university.isActive())throw new ResourceConflictException("Üniversite aktif değil.");
    }
    public void lifecycleAllowed(Locked ctx) {
        if(ctx.club().getStatus()!=ClubStatus.ACTIVE&&!core.policy.admin(ctx.actor()))core.policy.deny();
    }
    public void checkLink(Event event,AnnouncementAudience audience,boolean publishing) {
        if(event==null)return;
        if(publishing&&(event.getPublishedAt()==null||event.getArchivedAt()!=null))
            throw new ResourceConflictException("Bağlantılı etkinlik yayımlanmış ve arşivlenmemiş olmalı.");
        if(event.getAudience()==EventAudience.CLUB_MEMBERS&&audience==AnnouncementAudience.UNIVERSITY_STUDENTS)
            throw new ResourceConflictException("Kulübe özel etkinlik üniversite kitlesine açılamaz.");
    }
    public LinkedAnnouncementEvent link(Announcement a,User actor,ClubMembership local) {
        Event event=a.getEvent();
        return event!=null&&eventPolicy.visible(event,actor,local)?LinkedAnnouncementEvent.from(event):null;
    }
    public ManagedAnnouncementResponse response(Announcement a,User actor) {
        return ManagedAnnouncementResponse.from(a,link(a,actor,core.local(a.getClub(),actor.getId())));
    }
}
