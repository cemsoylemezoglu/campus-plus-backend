package com.campusplus.backend.repository;

import com.campusplus.backend.entity.*;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event,Long> {
    @Override @EntityGraph(attributePaths={"club","club.university"}) Optional<Event> findById(Long id);
    @EntityGraph(attributePaths={"club","club.university"}) Optional<Event> findByIdAndClubId(Long id,Long clubId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id=:id and e.club.id=:clubId")
    Optional<Event> findForUpdate(@Param("id") Long id,@Param("clubId") Long clubId);

    @EntityGraph(attributePaths={"club","club.university"})
    @Query("select e from Event e where e.status=com.campusplus.backend.entity.EventStatus.PUBLISHED "
        +"and e.archivedAt is null and e.club.status=com.campusplus.backend.entity.ClubStatus.ACTIVE "
        +"and (:clubId is null or e.club.id=:clubId) and (:universityId is null or e.club.university.id=:universityId) "
        // pgJDBC binds a null timestamptz with an unspecified OID; the IS NULL occurrence needs its own type.
        +"and (cast(:fromTime as instant) is null or e.startsAt>=:fromTime) "
        +"and (cast(:toTime as instant) is null or e.startsAt<:toTime) "
        +"and lower(e.title) like :pattern escape '!' and (:admin=true "
        +"or e.audience=com.campusplus.backend.entity.EventAudience.UNIVERSITY_STUDENTS "
        +"or exists(select m.id from ClubMembership m where m.club=e.club and m.userId=:userId "
        +"and m.status=com.campusplus.backend.entity.ClubMembershipStatus.ACTIVE))")
    Page<Event> discover(@Param("clubId") Long clubId,@Param("universityId") Long universityId,
        @Param("fromTime") Instant from,@Param("toTime") Instant to,@Param("pattern") String pattern,
        @Param("admin") boolean admin,@Param("userId") Long userId,Pageable pageable);

    @EntityGraph(attributePaths={"club","club.university"})
    @Query("select e from Event e where e.club.id=:clubId and (:status is null or e.status=:status)")
    Page<Event> managed(@Param("clubId") Long clubId,@Param("status") EventStatus status,Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.club.id=:clubId and e.status in :statuses order by e.id")
    List<Event> lockOpenForClub(@Param("clubId") Long clubId,@Param("statuses") Collection<EventStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.club.id=:clubId and e.status=com.campusplus.backend.entity.EventStatus.PUBLISHED "
        +"and e.audience=com.campusplus.backend.entity.EventAudience.CLUB_MEMBERS and e.endsAt>:now "
        +"and exists(select p.id from EventParticipation p where p.event=e and p.userId=:userId "
        +"and p.status=com.campusplus.backend.entity.EventParticipationStatus.REGISTERED) order by e.id")
    List<Event> lockMemberEvents(@Param("clubId") Long clubId,@Param("userId") Long userId,@Param("now") Instant now);
}
