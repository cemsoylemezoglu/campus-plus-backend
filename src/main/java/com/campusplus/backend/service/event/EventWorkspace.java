package com.campusplus.backend.service.event;

import com.campusplus.backend.dto.event.*;
import com.campusplus.backend.entity.*;
import com.campusplus.backend.exception.*;
import com.campusplus.backend.repository.*;
import com.campusplus.backend.security.EventPolicy;
import com.campusplus.backend.service.club.ClubWorkspace;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class EventWorkspace {
    public static final List<EventParticipationStatus> OCCUPIED=List.of(EventParticipationStatus.REGISTERED,EventParticipationStatus.ATTENDED);
    public final ClubWorkspace core;
    public final EventRepository events;
    public final EventParticipationRepository participants;
    public final EventPolicy policy;
    private final EntityManager em;
    public EventWorkspace(ClubWorkspace core,EventRepository events,EventParticipationRepository participants,EventPolicy policy,EntityManager em) {
        this.core=core;this.events=events;this.participants=participants;this.policy=policy;this.em=em;
    }
    public record Locked(Club club,User actor,Event event,Map<Long,User> users) {}
    public Event event(Long id){return events.findById(id).orElseThrow(() -> new ResourceNotFoundException("Etkinlik bulunamadı."));}
    public Event eventOfClub(Long id,Long clubId){return events.findByIdAndClubId(id,clubId)
            .orElseThrow(() -> new ResourceNotFoundException("Etkinlik bulunamadı."));}
    public void preflightManager(Long clubId) {
        Club c=core.club(clubId);User actor=core.actor();policy.requireManager(c,actor,core.local(c,actor.getId()));
    }
    public Locked managed(Long clubId,Long eventId,Collection<Long> affected) {
        preflightManager(clubId);return lock(clubId,eventId,affected,true);
    }
    public Locked visible(Long eventId) {
        Event preview=event(eventId);User actor=core.actor();
        policy.requireVisible(preview,actor,core.local(preview.getClub(),actor.getId()));
        var locked=lock(preview.getClub().getId(),eventId,List.of(),false);
        policy.requireVisible(locked.event(),locked.actor(),core.local(locked.club(),locked.actor().getId()));
        return locked;
    }
    public Locked owned(Long eventId) {
        Event preview=event(eventId);
        participants.findByEventIdAndUserId(eventId,core.current.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Katılım bulunamadı."));
        // An existing ticket remains cancellable if the club is suspended or becomes hidden.
        return lock(preview.getClub().getId(),eventId,List.of(),false);
    }
    private Locked lock(Long clubId,Long eventId,Collection<Long> affected,boolean managed) {
        var ids=new TreeSet<>(affected);ids.add(core.current.userId());var users=new LinkedHashMap<Long,User>();
        for(Long id:ids) users.put(id,core.lockUser(id));
        Club club=core.clubs.findByIdForShare(clubId).orElseThrow(() -> new ResourceNotFoundException("Kulüp bulunamadı."));
        em.refresh(club,LockModeType.PESSIMISTIC_READ);
        User actor=users.get(core.current.userId());
        if(managed) policy.requireManager(club,actor,core.local(club,actor.getId()));
        Event event=null;
        if(eventId!=null) {
            event=events.findForUpdate(eventId,clubId).orElseThrow(() -> new ResourceNotFoundException("Etkinlik bulunamadı."));
            em.refresh(event,LockModeType.PESSIMISTIC_WRITE);
        }
        return new Locked(club,actor,event,users);
    }
    public EventParticipation participation(Long eventId,Long participationId) {
        var p=participants.findByIdAndEventId(participationId,eventId).orElseThrow(() -> new ResourceNotFoundException("Katılım bulunamadı."));
        em.refresh(p);return p;
    }
    public EventParticipation own(Event e,Long userId) {
        var p=participants.findByEventIdAndUserId(e.getId(),userId).orElseThrow(() -> new ResourceNotFoundException("Katılım bulunamadı."));
        em.refresh(p);return p;
    }
    public void active(Club club) {
        core.policy.requireActive(club);
        University u=core.universities.findByIdForShare(club.getUniversity().getId()).orElseThrow(() -> new ResourceNotFoundException("Üniversite bulunamadı."));
        core.refreshUniversity(u);
        if(!u.isActive()) throw new ResourceConflictException("Üniversite aktif değil.");
    }
    public StudentProfile eligible(Event e,User user) {
        StudentProfile profile=core.eligible(user.getId(),e.getClub());
        policy.requireMemberAudience(e,user,core.local(e.getClub(),user.getId()));return profile;
    }
    public long occupied(Event e){return participants.countByEventIdAndStatusIn(e.getId(),OCCUPIED);}
    public EventSummary summary(Event e,long occupied,Instant now) {
        Long available=e.getCapacity()==null?null:Math.max(0L,e.getCapacity()-occupied);
        boolean open=e.getStatus()==EventStatus.PUBLISHED && e.getArchivedAt()==null
                && e.getClub().getStatus()==ClubStatus.ACTIVE && e.getClub().getUniversity().isActive()
                && !now.isBefore(e.getRegistrationOpensAt()) && now.isBefore(e.getRegistrationClosesAt())
                && (available==null||available>0);
        return new EventSummary(e.getId(),EventClubSummary.from(e.getClub()),e.getTitle(),e.getVenue(),e.getStartsAt(),e.getEndsAt(),
                e.getTimeZone(),e.getStatus(),e.getAudience(),e.getCapacity(),occupied,available,open,e.getArchivedAt(),e.getVersion());
    }
    public EventResponse response(Event e) {
        return new EventResponse(summary(e,occupied(e),core.clock.instant()),e.getDescription(),e.getCoverUrl(),e.getRegistrationOpensAt(),
                e.getRegistrationClosesAt(),e.getPublishedAt(),e.getCancelledAt(),e.getCancellationReason(),e.getCompletedAt(),
                e.getScheduleChangedAt(),e.getScheduleChangeReason(),e.getCreatedAt(),e.getUpdatedAt());
    }
}
