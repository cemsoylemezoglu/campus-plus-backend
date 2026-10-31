package com.campusplus.backend.repository;

import com.campusplus.backend.entity.*;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AnnouncementRepository extends JpaRepository<Announcement,Long> {
    @Override @EntityGraph(attributePaths={"club","club.university","event"}) Optional<Announcement> findById(Long id);
    @EntityGraph(attributePaths={"club","club.university","event"}) Optional<Announcement> findByIdAndClubId(Long id,Long clubId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Announcement a where a.id=:id and a.club.id=:clubId")
    Optional<Announcement> findForUpdate(@Param("id") Long id,@Param("clubId") Long clubId);

    @EntityGraph(attributePaths={"club","club.university"})
    @Query("select a from Announcement a where a.status=com.campusplus.backend.entity.AnnouncementStatus.PUBLISHED "
        +"and a.archivedAt is null and a.club.status=com.campusplus.backend.entity.ClubStatus.ACTIVE and a.club.university.active=true "
        +"and (:clubId is null or a.club.id=:clubId) and (:universityId is null or a.club.university.id=:universityId) "
        +"and lower(a.title) like :pattern escape '!' and (:admin=true or ("
        +"exists(select s.userId from StudentProfile s where s.userId=:userId and s.university=a.club.university) "
        +"and (a.audience=com.campusplus.backend.entity.AnnouncementAudience.UNIVERSITY_STUDENTS "
        +"or exists(select m.id from ClubMembership m where m.club=a.club and m.userId=:userId "
        +"and m.status=com.campusplus.backend.entity.ClubMembershipStatus.ACTIVE))))")
    Page<Announcement> discover(@Param("clubId") Long clubId,@Param("universityId") Long universityId,
        @Param("pattern") String pattern,@Param("admin") boolean admin,@Param("userId") Long userId,Pageable pageable);

    @EntityGraph(attributePaths={"club","club.university"})
    @Query("select a from Announcement a where a.club.id=:clubId and (:status is null or a.status=:status) "
        +"and (:archived is null or (:archived=true and a.archivedAt is not null) or (:archived=false and a.archivedAt is null))")
    Page<Announcement> managed(@Param("clubId") Long clubId,@Param("status") AnnouncementStatus status,
        @Param("archived") Boolean archived,Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Announcement a where a.club.id=:clubId and a.archivedAt is null order by a.id")
    List<Announcement> lockUnarchived(@Param("clubId") Long clubId);
}
