package com.campusplus.backend.security;

import com.campusplus.backend.entity.*;
import com.campusplus.backend.exception.ResourceNotFoundException;
import org.springframework.stereotype.Component;

/** Normal social access. Administrative draft inspection is deliberately a separate capability. */
@Component
public class ClubInteractionPolicy {
    private final ClubPolicy clubs;
    public ClubInteractionPolicy(ClubPolicy clubs) { this.clubs = clubs; }

    public boolean readable(Event event, User actor, ClubMembership membership, StudentProfile profile) {
        if (event == null || actor == null || !active(event.getClub()) || event.getPublishedAt() == null
                || event.getArchivedAt() != null) return false;
        boolean publishedState = switch (event.getStatus()) {
            case PUBLISHED, COMPLETED, CANCELLED -> true;
            default -> false;
        };
        return publishedState && (event.getAudience() == EventAudience.UNIVERSITY_STUDENTS
                || sameUniversity(event.getClub(), actor, profile) && member(event.getClub(), actor, membership));
    }

    public boolean readable(Announcement announcement, User actor, ClubMembership membership, StudentProfile profile) {
        return announcement != null && actor != null && active(announcement.getClub())
                && announcement.getStatus() == AnnouncementStatus.PUBLISHED && announcement.getPublishedAt() != null
                && announcement.getArchivedAt() == null && sameUniversity(announcement.getClub(), actor, profile)
                && (announcement.getAudience() == AnnouncementAudience.UNIVERSITY_STUDENTS
                    || member(announcement.getClub(), actor, membership));
    }

    public boolean writable(Event event, User actor, ClubMembership membership, StudentProfile profile) {
        return readable(event, actor, membership, profile) && actor.getRoles().contains(Role.STUDENT)
                && sameUniversity(event.getClub(), actor, profile) && event.getStatus() != EventStatus.CANCELLED;
    }

    public boolean writable(Announcement announcement, User actor, ClubMembership membership, StudentProfile profile) {
        return readable(announcement, actor, membership, profile) && actor.getRoles().contains(Role.STUDENT);
    }

    public boolean resourceManager(Event event, User actor, ClubMembership membership, StudentProfile profile) {
        return readable(event, actor, membership, profile) && sameUniversity(event.getClub(), actor, profile)
                && localManager(event.getClub(), actor, membership);
    }

    public boolean resourceManager(Announcement announcement, User actor, ClubMembership membership, StudentProfile profile) {
        return readable(announcement, actor, membership, profile) && localManager(announcement.getClub(), actor, membership);
    }

    public void requireReadable(Event event, User actor, ClubMembership membership, StudentProfile profile) {
        if (!readable(event, actor, membership, profile)) missing();
    }

    public void requireReadable(Announcement announcement, User actor, ClubMembership membership, StudentProfile profile) {
        if (!readable(announcement, actor, membership, profile)) missing();
    }

    private boolean active(Club club) {
        return club != null && club.getStatus() == ClubStatus.ACTIVE && club.getUniversity().isActive();
    }

    private boolean sameUniversity(Club club, User actor, StudentProfile profile) {
        return profile != null && profile.getUserId().equals(actor.getId())
                && profile.getUniversity().getId().equals(club.getUniversity().getId());
    }

    private boolean member(Club club, User actor, ClubMembership membership) {
        return membership != null && membership.getClub().getId().equals(club.getId())
                && membership.getUserId().equals(actor.getId()) && membership.getStatus() == ClubMembershipStatus.ACTIVE;
    }

    private boolean localManager(Club club, User actor, ClubMembership membership) {
        return member(club, actor, membership) && clubs.manager(actor, membership);
    }

    private void missing() { throw new ResourceNotFoundException("İçerik bulunamadı."); }
}
