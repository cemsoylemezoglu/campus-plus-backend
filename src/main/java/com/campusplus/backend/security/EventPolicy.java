package com.campusplus.backend.security;

import com.campusplus.backend.entity.*;
import com.campusplus.backend.exception.*;
import java.time.*;
import org.springframework.stereotype.Component;

@Component
public class EventPolicy {
    private final ClubPolicy clubs;
    public EventPolicy(ClubPolicy clubs){this.clubs=clubs;}
    public boolean member(Club club,User actor,ClubMembership m) {
        return m!=null && m.getClub().getId().equals(club.getId()) && m.getUserId().equals(actor.getId())
                && m.getStatus()==ClubMembershipStatus.ACTIVE;
    }
    public boolean manager(Club club,User actor,ClubMembership m) {
        return clubs.admin(actor) || (member(club,actor,m) && clubs.manager(actor,m));
    }
    public void requireManager(Club club,User actor,ClubMembership m) {if(!manager(club,actor,m)) clubs.deny();}
    public boolean visible(Event event,User actor,ClubMembership m) {
        if(manager(event.getClub(),actor,m)) return true;
        ClubMembership local=m!=null && m.getClub().getId().equals(event.getClub().getId())
                && m.getUserId().equals(actor.getId())?m:null;
        return event.getPublishedAt()!=null && clubs.visible(event.getClub(),actor,local)
                && (event.getAudience()==EventAudience.UNIVERSITY_STUDENTS || member(event.getClub(),actor,m));
    }
    public void requireVisible(Event event,User actor,ClubMembership m) {
        if(!visible(event,actor,m)) throw new ResourceNotFoundException("Etkinlik bulunamadı.");
    }
    public void requireMemberAudience(Event event,User actor,ClubMembership membership) {
        if(event.getAudience()==EventAudience.CLUB_MEMBERS && !member(event.getClub(),actor,membership))
            throw new ResourceConflictException("Bu etkinlik için aktif kulüp üyeliği gerekiyor.");
    }
    public void requireAdministrativeLifecycle(Club club,User actor) {
        if(club.getStatus()!=ClubStatus.ACTIVE && !clubs.admin(actor)) clubs.deny();
    }
    public void requireOpen(Event e,Instant now) {
        requireStatus(e,EventStatus.PUBLISHED);
        if(e.getArchivedAt()!=null || now.isBefore(e.getRegistrationOpensAt()) || !now.isBefore(e.getRegistrationClosesAt()))
            throw new ResourceConflictException("Etkinlik kayıt aralığı açık değil.");
    }
    public void requireStatus(Event e,EventStatus expected) {
        if(e.getStatus()!=expected || e.getArchivedAt()!=null) throw new ResourceConflictException("Etkinlik bu işleme uygun değil.");
    }
    public void requireMutable(Event e,Instant now) {
        if(e.getArchivedAt()!=null || (e.getStatus()!=EventStatus.DRAFT && e.getStatus()!=EventStatus.PUBLISHED)
                || !now.isBefore(e.getStartsAt())) throw new ResourceConflictException("Etkinlik düzenlemeye kapalı.");
    }
}
