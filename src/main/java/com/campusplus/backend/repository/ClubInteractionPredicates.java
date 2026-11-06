package com.campusplus.backend.repository;
/** Ordinary interaction visibility: deliberately excludes administrative draft bypass. */
public interface ClubInteractionPredicates {
    String EVENT_ACCESS="(e.audience=com.campusplus.backend.entity.EventAudience.UNIVERSITY_STUDENTS or ("
        +"exists(select s.userId from StudentProfile s where s.userId=:userId and s.university=eu) and "
        +"exists(select m.id from ClubMembership m where m.club=ec and m.userId=:userId and m.status=com.campusplus.backend.entity.ClubMembershipStatus.ACTIVE))) ";
    String ANNOUNCEMENT_ACCESS="exists(select s.userId from StudentProfile s where s.userId=:userId and s.university=nu) and "
        +"(n.audience=com.campusplus.backend.entity.AnnouncementAudience.UNIVERSITY_STUDENTS or "
        +"exists(select m.id from ClubMembership m where m.club=nc and m.userId=:userId and m.status=com.campusplus.backend.entity.ClubMembershipStatus.ACTIVE)) ";
    String VISIBLE="((e.id is not null and e.publishedAt is not null and e.archivedAt is null "
        +"and e.status in (com.campusplus.backend.entity.EventStatus.PUBLISHED,com.campusplus.backend.entity.EventStatus.COMPLETED,com.campusplus.backend.entity.EventStatus.CANCELLED) "
        +"and ec.status=com.campusplus.backend.entity.ClubStatus.ACTIVE and eu.active=true and "+EVENT_ACCESS+") or "
        +"(n.id is not null and n.publishedAt is not null and n.archivedAt is null and n.status=com.campusplus.backend.entity.AnnouncementStatus.PUBLISHED "
        +"and nc.status=com.campusplus.backend.entity.ClubStatus.ACTIVE and nu.active=true and "+ANNOUNCEMENT_ACCESS+")) ";
}
