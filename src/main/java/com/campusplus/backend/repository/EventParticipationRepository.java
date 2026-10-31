package com.campusplus.backend.repository;

import com.campusplus.backend.dto.event.ManagedEventParticipationResponse;
import com.campusplus.backend.entity.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface EventParticipationRepository extends JpaRepository<EventParticipation,Long> {
    Optional<EventParticipation> findByEventIdAndUserId(Long eventId,Long userId);
    Optional<EventParticipation> findByIdAndEventId(Long id,Long eventId);
    long countByEventIdAndStatusIn(Long eventId,Collection<EventParticipationStatus> statuses);
    boolean existsByEventIdAndStatus(Long eventId,EventParticipationStatus status);
    interface SeatCount {Long getEventId();long getAmount();}
    @Query("select p.event.id as eventId,count(p) as amount from EventParticipation p "
        +"where p.event.id in :ids and p.status in :statuses group by p.event.id")
    List<SeatCount> counts(@Param("ids") Collection<Long> ids,@Param("statuses") Collection<EventParticipationStatus> statuses);
    @EntityGraph(attributePaths={"event","event.club"})
    @Query("select p from EventParticipation p where p.userId=:userId and (:status is null or p.status=:status)")
    Page<EventParticipation> own(@Param("userId") Long userId,@Param("status") EventParticipationStatus status,Pageable pageable);
    @Query("select new com.campusplus.backend.dto.event.ManagedEventParticipationResponse(p.id,p.userId,u.name,p.status,"
        +"p.registeredAt,p.endedAt,p.endCause,p.decisionReason,p.checkedInAt,p.version) "
        +"from EventParticipation p join p.studentProfile s join s.user u where p.event.id=:eventId "
        +"and (:status is null or p.status=:status)")
    Page<ManagedEventParticipationResponse> managed(@Param("eventId") Long eventId,
        @Param("status") EventParticipationStatus status,Pageable pageable);
    @Query("select count(p)>0 from EventParticipation p where p.userId=:userId "
        +"and p.status in (com.campusplus.backend.entity.EventParticipationStatus.REGISTERED,"
        +"com.campusplus.backend.entity.EventParticipationStatus.ATTENDED) "
        +"and p.event.status=com.campusplus.backend.entity.EventStatus.PUBLISHED and p.event.endsAt>:now")
    boolean blocksUniversityChange(@Param("userId") Long userId,@Param("now") Instant now);
    // Called with club/event locks held. No participation entities are loaded for these bulk paths.
    @Modifying(flushAutomatically=true)
    @Query("update EventParticipation p set p.status=:status,p.endedAt=:now,p.endedBy=:actor,p.endCause=:cause,"
        +"p.decisionReason=:reason,p.updatedAt=:now,p.version=p.version+1 where p.event.id=:eventId "
        +"and p.status=com.campusplus.backend.entity.EventParticipationStatus.REGISTERED "
        +"and (:userId is null or p.userId=:userId)")
    int endRegistered(@Param("eventId") Long eventId,@Param("userId") Long userId,
        @Param("status") EventParticipationStatus status,@Param("cause") EventEndCause cause,
        @Param("actor") User actor,@Param("reason") String reason,@Param("now") Instant now);
}
