package com.campusplus.backend.security;

import com.campusplus.backend.entity.*;
import com.campusplus.backend.exception.*;
import org.springframework.stereotype.Component;

@Component
public class AnnouncementPolicy {
    private final ClubPolicy clubs;
    public AnnouncementPolicy(ClubPolicy clubs){this.clubs=clubs;}
    public boolean manager(Club club,User actor,ClubMembership membership) {
        return clubs.admin(actor)||(membership!=null&&membership.getClub().getId().equals(club.getId())&&clubs.manager(actor,membership));
    }
    public void requireManager(Club club,User actor,ClubMembership membership) {
        if(!manager(club,actor,membership)) clubs.deny();
    }
    public boolean visible(Announcement a,User actor,ClubMembership membership,StudentProfile profile) {
        if(manager(a.getClub(),actor,membership)) return true;
        return a.getStatus()==AnnouncementStatus.PUBLISHED&&a.getArchivedAt()==null
            &&a.getClub().getStatus()==ClubStatus.ACTIVE&&a.getClub().getUniversity().isActive()
            &&profile!=null&&profile.getUserId().equals(actor.getId())
            &&profile.getUniversity().getId().equals(a.getClub().getUniversity().getId())
            &&(a.getAudience()==AnnouncementAudience.UNIVERSITY_STUDENTS
                ||membership!=null&&membership.getClub().getId().equals(a.getClub().getId())
                &&membership.getUserId().equals(actor.getId())&&membership.getStatus()==ClubMembershipStatus.ACTIVE);
    }
    public void requireVisible(Announcement a,User actor,ClubMembership membership,StudentProfile profile) {
        if(!visible(a,actor,membership,profile)) throw new ResourceNotFoundException("Duyuru bulunamadı.");
    }
    public void requireMutable(Announcement a) {
        if(a.getArchivedAt()!=null||a.getStatus()==AnnouncementStatus.WITHDRAWN)
            throw new ResourceConflictException("Duyuru düzenlemeye kapalı.");
    }
}
